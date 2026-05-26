package com.aidevassistant.graph;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class SdlcGraphBuilder {

    private final SdlcNodes nodes;
    private final MemorySaver checkpointSaver = new MemorySaver();

    @Value("${agent.sdlc.recursion-limit:50}")
    private int recursionLimit;

    private volatile CompiledGraph<SdlcState> graphWithHuman;
    private volatile CompiledGraph<SdlcState> graphWithoutHuman;

    public SdlcGraphBuilder(SdlcNodes nodes) {
        this.nodes = nodes;
    }

    public CompiledGraph<SdlcState> graph(boolean awaitHuman) {
        if (awaitHuman) {
            CompiledGraph<SdlcState> cached = graphWithHuman;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (graphWithHuman == null) {
                    graphWithHuman = compileGraph(true);
                }
                return graphWithHuman;
            }
        }
        CompiledGraph<SdlcState> cached = graphWithoutHuman;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (graphWithoutHuman == null) {
                graphWithoutHuman = compileGraph(false);
            }
            return graphWithoutHuman;
        }
    }

    private CompiledGraph<SdlcState> compileGraph(boolean awaitHuman) {
        try {
            return buildGraph(awaitHuman);
        } catch (GraphStateException e) {
            throw new IllegalStateException("Failed to compile SDLC graph", e);
        }
    }

    private CompiledGraph<SdlcState> buildGraph(boolean awaitHuman) throws GraphStateException {
        StateGraph<SdlcState> graph = new StateGraph<>(SdlcState.SCHEMA, SdlcState::new)
                .addNode(SdlcGraphRouter.REQUIREMENTS, node_async(nodes::requirements))
                .addNode(SdlcGraphRouter.ARCHITECTURE, node_async(nodes::architecture))
                .addNode(SdlcGraphRouter.IMPLEMENTATION, node_async(nodes::implementation))
                .addNode(SdlcGraphRouter.TESTING, node_async(nodes::testing))
                .addNode(SdlcGraphRouter.REVIEW, node_async(nodes::review))
                .addNode(SdlcGraphRouter.APPROVAL, node_async(nodes::approval))
                .addEdge(START, SdlcGraphRouter.REQUIREMENTS)
                .addConditionalEdges(
                        SdlcGraphRouter.REQUIREMENTS,
                        edge_async(SdlcGraphRouter::afterRequirements),
                        Map.of(
                                SdlcGraphRouter.ARCHITECTURE, SdlcGraphRouter.ARCHITECTURE,
                                SdlcGraphRouter.END, END
                        )
                )
                .addConditionalEdges(
                        SdlcGraphRouter.ARCHITECTURE,
                        edge_async(SdlcGraphRouter::afterArchitecture),
                        Map.of(
                                SdlcGraphRouter.IMPLEMENTATION, SdlcGraphRouter.IMPLEMENTATION,
                                SdlcGraphRouter.END, END
                        )
                )
                .addConditionalEdges(
                        SdlcGraphRouter.IMPLEMENTATION,
                        edge_async(SdlcGraphRouter::afterImplementation),
                        Map.of(
                                SdlcGraphRouter.TESTING, SdlcGraphRouter.TESTING,
                                SdlcGraphRouter.END, END
                        )
                )
                .addConditionalEdges(
                        SdlcGraphRouter.TESTING,
                        edge_async(SdlcGraphRouter::afterTesting),
                        Map.of(
                                SdlcGraphRouter.REVIEW, SdlcGraphRouter.REVIEW,
                                SdlcGraphRouter.APPROVAL, SdlcGraphRouter.APPROVAL,
                                SdlcGraphRouter.IMPLEMENTATION, SdlcGraphRouter.IMPLEMENTATION,
                                SdlcGraphRouter.END, END
                        )
                )
                .addConditionalEdges(
                        SdlcGraphRouter.REVIEW,
                        edge_async(SdlcGraphRouter::afterReview),
                        Map.of(
                                SdlcGraphRouter.IMPLEMENTATION, SdlcGraphRouter.IMPLEMENTATION,
                                SdlcGraphRouter.APPROVAL, SdlcGraphRouter.APPROVAL,
                                SdlcGraphRouter.END, END
                        )
                )
                .addConditionalEdges(
                        SdlcGraphRouter.APPROVAL,
                        edge_async(SdlcGraphRouter::afterApproval),
                        Map.of(
                                SdlcGraphRouter.IMPLEMENTATION, SdlcGraphRouter.IMPLEMENTATION,
                                SdlcGraphRouter.END, END
                        )
                );

        CompileConfig.Builder compile = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .recursionLimit(Math.max(25, recursionLimit))
                .releaseThread(true);
        if (awaitHuman) {
            compile.interruptBefore(SdlcGraphRouter.APPROVAL);
        }
        return graph.compile(compile.build());
    }
}
