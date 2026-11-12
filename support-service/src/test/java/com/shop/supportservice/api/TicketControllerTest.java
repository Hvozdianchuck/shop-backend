package com.shop.supportservice.api;

import com.shop.supportservice.FakeAiConfiguration;
import com.shop.supportservice.TestcontainersConfiguration;
import com.shop.supportservice.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class TicketControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TicketRepository tickets;

    @Test
    void acceptsTicketAndStoresMaskedText() {
        var result = mvc.post().uri("/api/support/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text": "  Where is my order? Reach me at anna@example.com or +48 600 123 456  "}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        var id = UUID.fromString(result.getResponse().getHeader("Location").replaceAll(".*/", ""));
        var stored = tickets.findById(id).orElseThrow();
        assertThat(stored.getTextMasked()).isEqualTo("Where is my order? Reach me at [EMAIL] or [PHONE]");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("NEW");
    }

    @Test
    void truncatesLongText() {
        var result = mvc.post().uri("/api/support/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"" + "a".repeat(2500) + "\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.text").asString()
                .hasSize(2000 + "\n[truncated]".length())
                .endsWith("\n[truncated]");
    }

    @Test
    void rejectsBlankText() {
        assertThat(mvc.post().uri("/api/support/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"  \"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returnsNotFoundForUnknownTicket() {
        assertThat(mvc.get().uri("/api/support/tickets/{id}", UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
