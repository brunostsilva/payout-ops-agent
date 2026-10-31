package io.github.brunostsilva.payoutops.agent;

import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.BRL;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.SELLER;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.chargeback;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.commission;
import static io.github.brunostsilva.payoutops.domain.LedgerFixtures.sale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.brunostsilva.payoutops.application.InMemoryPayoutHoldRepository;
import io.github.brunostsilva.payoutops.application.PayoutHoldService;
import io.github.brunostsilva.payoutops.application.PayoutService;
import io.github.brunostsilva.payoutops.domain.HoldStatus;
import io.github.brunostsilva.payoutops.domain.LedgerEntry;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.Seller;
import io.github.brunostsilva.payoutops.domain.SellerRepository;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.json.JsonMapper;

/** Runs the real Spring AI tool-calling loop against in-memory data, without Spring or Docker. */
class PayoutAgentTest {

    private static final PayoutPeriod CURRENT = PayoutPeriod.weekOf(LocalDate.of(2026, 10, 26));

    private final List<LedgerEntry> ledger = List.of(
            sale("1000.00", CURRENT.previous().start()),
            commission("100.00", CURRENT.previous().start()),
            sale("800.00", CURRENT.start()),
            commission("80.00", CURRENT.start()),
            chargeback("100.00", CURRENT.start()));

    private final SellerRepository sellers =
            id -> id.equals(SELLER) ? Optional.of(new Seller(SELLER, "Test Seller", BRL)) : Optional.empty();

    private final PayoutService payoutService = new PayoutService(
            sellers,
            (sellerId, period) -> ledger.stream()
                    .filter(entry -> entry.sellerId().equals(sellerId) && period.contains(entry.occurredOn()))
                    .toList(),
            sellerId -> List.of());

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private final ObservationRegistry observations = ObservationRegistry.NOOP;
    private final InMemoryPayoutHoldRepository holds = new InMemoryPayoutHoldRepository();
    private final PayoutTools tools = new PayoutTools(payoutService);
    private final PayoutActionTools actionTools =
            new PayoutActionTools(new PayoutHoldService(sellers, holds, clock));
    private final PayoutAgent agent =
            new PayoutAgent(new ScriptedChatModel(new JsonMapper()), tools, actionTools, clock, observations);

    @Test
    void answersThroughTheComparisonToolAndTracesTheCall() {
        AgentAnswer answer = agent.ask("Why did seller-1's payout change in the week of 2026-10-26?");

        // previous week: 1000 - 100 = 900; current week: 800 - 80 - 100 = 620
        assertThat(answer.answer())
                .contains("BRL -280.00 (-31.1%)")
                .contains("sales (BRL -200.00), chargebacks (BRL -100.00), commissions (BRL 20.00)");
        assertThat(answer.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.tool()).isEqualTo("comparePayoutWithPreviousWeek");
            assertThat(call.arguments()).contains("seller-1").contains("2026-10-26");
            assertThat(call.result()).contains("\"netChange\"");
        });
    }

    @Test
    void toolErrorsAreFedBackToTheModelInsteadOfFailingTheRequest() {
        AgentAnswer answer = agent.ask("Explain seller-999's payout for 2026-10-26");

        assertThat(answer.answer()).contains("couldn't get the payout data").contains("seller-999");
        assertThat(answer.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.result()).contains("seller-999"));
    }

    @Test
    void questionsWithoutSellerOrDateGetGuidanceAndNoToolCalls() {
        AgentAnswer answer = agent.ask("Why is my payout lower?");

        assertThat(answer.toolCalls()).isEmpty();
        assertThat(answer.answer()).contains("seller id");
    }

    @Test
    void tracesParallelToolCallsWithEmptyIdsInOrder() {
        // Some providers (e.g. Ollama) may send tool calls without ids: results must still match their calls.
        ChatModel parallelCallsWithoutIds = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (prompt.getInstructions().getLast() instanceof ToolResponseMessage) {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
                }
                AssistantMessage toolCalls = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                new AssistantMessage.ToolCall(
                                        "", "function", "getWeeklyPayout",
                                        "{\"sellerId\":\"seller-1\",\"weekOf\":\"2026-10-26\"}"),
                                new AssistantMessage.ToolCall(
                                        "", "function", "getCommissionRules", "{\"sellerId\":\"seller-1\"}")))
                        .build();
                return new ChatResponse(List.of(new Generation(toolCalls)));
            }
        };

        AgentAnswer answer = new PayoutAgent(parallelCallsWithoutIds, tools, actionTools, clock, observations).ask("anything");

        assertThat(answer.toolCalls()).extracting(ToolInvocation::tool)
                .containsExactly("getWeeklyPayout", "getCommissionRules");
        assertThat(answer.toolCalls().get(0).result()).contains("\"grossSales\"");
        assertThat(answer.toolCalls().get(1).result()).isEqualTo("[]");
    }

    @Test
    void addsToolsOnTopOfTheProviderOptionsInsteadOfReplacingThem() {
        // OpenAiChatModel/OllamaChatModel cast prompt options to their own type and don't merge defaults.
        OpenAiChatOptions providerDefaults = OpenAiChatOptions.builder().model("gpt-test").temperature(0.0).build();
        List<ChatOptions> sentOptions = new ArrayList<>();
        ChatModel providerLike = new ChatModel() {
            @Override
            public ChatOptions getOptions() {
                return providerDefaults;
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                sentOptions.add(prompt.getOptions());
                return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
            }
        };

        new PayoutAgent(providerLike, tools, actionTools, clock, observations).ask("anything");

        assertThat(sentOptions).singleElement().isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("gpt-test");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getToolCallbacks())
                    .extracting(callback -> callback.getToolDefinition().name())
                    .contains("comparePayoutWithPreviousWeek", "getLedgerEntries");
        });
    }

    @Test
    void proposesAPendingHoldOnlyWhenAskedAndSaysNothingWasHeld() {
        AgentAnswer answer =
                agent.ask("Please hold seller-1's payout for the week of 2026-10-26, the chargeback looks like fraud");

        assertThat(answer.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.tool()).isEqualTo("proposePayoutHold"));
        assertThat(holds.findByStatus(HoldStatus.PENDING)).singleElement().satisfies(hold -> {
            assertThat(hold.sellerId()).isEqualTo(SELLER);
            assertThat(hold.period()).isEqualTo(CURRENT);
            assertThat(hold.proposedBy()).isEqualTo("payout-ops-agent");
            assertThat(answer.answer())
                    .contains(hold.id().toString())
                    .contains("Status: PENDING")
                    .contains("Nothing has been held");
        });
    }

    @Test
    void aSecondRequestForAPendingWeekIsReportedAsReusedNotAsANewProposal() {
        agent.ask("Hold seller-1's payout for the week of 2026-10-26, first analyst");

        AgentAnswer second = agent.ask("Hold seller-1's payout for the week of 2026-10-26, second analyst");

        assertThat(holds.findByStatus(HoldStatus.PENDING)).hasSize(1);
        assertThat(second.answer())
                .contains("already has a pending hold proposal")
                .contains("first analyst")
                .doesNotContain("Proposed a payout hold");
        assertThat(second.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.result()).contains("\"created\":false"));
    }

    @Test
    void stopsAModelThatNeverStopsCallingTools() {
        ChatModel alwaysCallsTools = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                AssistantMessage toolCall = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call", "function", "getCommissionRules", "{\"sellerId\":\"seller-1\"}")))
                        .build();
                return new ChatResponse(List.of(new Generation(toolCall)));
            }
        };

        assertThatThrownBy(() -> new PayoutAgent(alwaysCallsTools, tools, actionTools, clock, observations).ask("anything"))
                .isInstanceOf(AgentLoopLimitExceededException.class)
                .hasMessageContaining(String.valueOf(PayoutAgent.MAX_TOOL_ROUNDS));
    }
}
