package com.example.plugin;

import com.example.api.TodoApi;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.PluginServiceHub;
import net.corda.webserver.services.WebServerPluginRegistry;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class TodoPlugin implements WebServerPluginRegistry {
    /**
     * A list of classes that expose web APIs.
     */
    private final List<Function<CordaRPCOps, ?>> webApis = ImmutableList.of(TodoApi::new);

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     */
    private final Map<String, String> staticServeDirs = ImmutableMap.of(
            // This will serve the exampleWeb directory in resources to /web/todo
            "todo", getClass().getClassLoader().getResource("todoWeb").toExternalForm()
    );

    @Override public List<Function<CordaRPCOps, ?>> getWebApis() { return webApis; }
    @Override public Map<String, String> getStaticServeDirs() { return staticServeDirs; }
}