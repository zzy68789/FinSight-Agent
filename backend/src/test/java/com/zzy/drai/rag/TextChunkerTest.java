package com.zzy.drai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void splitsTextWithConfiguredOverlap() {
        TextChunker chunker = new TextChunker(10, 3);

        List<String> chunks = chunker.chunk("abcdefghijklmnopqrstuvwxyz");

        assertThat(chunks).containsExactly("abcdefghij", "hijklmnopq", "opqrstuvwx", "vwxyz");
    }

    @Test
    void returnsEmptyListForBlankText() {
        TextChunker chunker = new TextChunker(10, 3);

        assertThat(chunker.chunk("   ")).isEmpty();
    }
}
