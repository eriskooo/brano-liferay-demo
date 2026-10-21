package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Trasy z docker/nginx/default.conf – vstupný bod stranglera: ktorá cesta ide do starej a ktorá do novej appky. */
public class NginxExtractor implements Extractor {

    static final String NGINX_CONF = "docker/nginx/default.conf";

    private static final Pattern LOCATION = Pattern.compile(
            "location\\s+(=\\s*)?(\\S+)\\s*\\{(.*?)\\n\\s*}", Pattern.DOTALL);
    private static final Pattern PROXY_PASS = Pattern.compile("proxy_pass\\s+([^;]+);");
    private static final Pattern SET = Pattern.compile("set\\s+(\\$\\w+)\\s+([^;]+);");
    private static final Pattern RETURN = Pattern.compile("return\\s+30\\d\\s+([^;]+);");
    private static final Pattern URL_HOST = Pattern.compile("https?://([\\w.-]+)");

    private record Location(String id, String path, boolean exact, String body) {
    }

    @Override
    public void extract(Path repoRoot, GraphBuilder graph) throws IOException {
        Path file = repoRoot.resolve(NGINX_CONF);
        if (!Files.isRegularFile(file)) {
            return;
        }
        String conf = Files.readString(file, StandardCharsets.UTF_8);
        List<Location> locations = new ArrayList<>();
        Matcher m = LOCATION.matcher(conf);
        while (m.find()) {
            boolean exact = m.group(1) != null;
            String path = m.group(2);
            // "location /" a "location = /" by mali rovnaké id – catch-all dostane "/*".
            String id = !exact && path.equals("/") ? "/*" : path;
            locations.add(new Location(id, path, exact, m.group(3)));
        }
        NodeRef nginx = new NodeRef("Service", "nginx");
        locations.forEach(loc -> addRoute(nginx, loc, locations, graph));
    }

    private void addRoute(NodeRef nginx, Location loc, List<Location> all, GraphBuilder graph) {
        NodeRef self = new NodeRef("Route", loc.id());
        Map<String, Object> props = new HashMap<>();
        if (loc.exact()) {
            props.put("exact", true);
        }
        Optional<String> proxyHost = proxyHost(loc.body());
        Optional<String> redirect = find(RETURN, loc.body());
        props.put("kind", redirect.isPresent() ? "redirect" : "proxy");
        graph.node("Route", loc.id(), props);
        graph.edge("ROUTES", nginx, self);
        proxyHost.ifPresent(host -> graph.edge("PROXIES_TO", self, new NodeRef("Service", host)));
        redirect.flatMap(target -> bestMatch(target.trim(), all))
                .ifPresent(target -> graph.edge("REDIRECTS_TO", self, new NodeRef("Route", target.id())));
    }

    private static Optional<String> proxyHost(String body) {
        return find(PROXY_PASS, body).map(target -> {
            String value = target.trim();
            if (value.startsWith("$")) {
                // proxy_pass cez premennú (voliteľný kontajner) – hodnotu dosadíme zo "set" v tom istom bloku.
                Matcher set = SET.matcher(body);
                while (set.find()) {
                    if (set.group(1).equals(value)) {
                        return set.group(2).trim();
                    }
                }
            }
            return value;
        }).flatMap(url -> find(URL_HOST, url));
    }

    /** Location, ktorú nginx vyberie pre cieľ presmerovania: presná zhoda, inak najdlhší prefix. */
    private static Optional<Location> bestMatch(String target, List<Location> all) {
        Optional<Location> exact = all.stream().filter(l -> l.exact() && l.path().equals(target)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        return all.stream()
                .filter(l -> !l.exact() && target.startsWith(l.path()))
                .max(Comparator.comparingInt(l -> l.path().length()));
    }

    private static Optional<String> find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
