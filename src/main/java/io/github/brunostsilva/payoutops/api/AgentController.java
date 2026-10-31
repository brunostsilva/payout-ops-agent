package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.agent.AgentAnswer;
import io.github.brunostsilva.payoutops.agent.PayoutAgent;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
class AgentController {

    private final PayoutAgent agent;

    AgentController(PayoutAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/ask")
    AgentAnswer ask(@Valid @RequestBody AskRequest request) {
        return agent.ask(request.question());
    }
}
