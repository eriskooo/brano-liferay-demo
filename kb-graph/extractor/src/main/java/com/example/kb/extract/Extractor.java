package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import java.io.IOException;
import java.nio.file.Path;

/** Jeden zdroj faktov z kódu (POM, compose, nginx…). Štruktúra sa nepíše ručne – každý fakt má svoj extraktor. */
public interface Extractor {

    /** Prečíta svoje súbory pod koreňom repa a pridá uzly a hrany do grafu. */
    void extract(Path repoRoot, GraphBuilder graph) throws IOException;
}
