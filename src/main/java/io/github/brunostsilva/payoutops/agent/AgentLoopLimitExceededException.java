package io.github.brunostsilva.payoutops.agent;

public class AgentLoopLimitExceededException extends RuntimeException {

    public AgentLoopLimitExceededException(int maxToolRounds) {
        super("The model kept requesting tools after " + maxToolRounds + " rounds without producing an answer");
    }
}
