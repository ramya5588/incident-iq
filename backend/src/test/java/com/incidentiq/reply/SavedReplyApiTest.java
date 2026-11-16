package com.incidentiq.reply;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.TestAuth;
import com.incidentiq.TestcontainersConfiguration;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestAuth.Csrf.class})
@Transactional
class SavedReplyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UserAccount admin;
    private UserAccount alex;
    private UserAccount sam;
    private UserAccount riley;

    @BeforeEach
    void setUp() {
        // Start from no replies, whatever else is in the database.
        jdbc.update("DELETE FROM saved_replies");
        admin = save("reply.ada@incidentiq.test", "Ada Admin", UserRole.ADMIN);
        alex = save("reply.alex@incidentiq.test", "Alex Agent", UserRole.AGENT);
        sam = save("reply.sam@incidentiq.test", "Sam Agent", UserRole.AGENT);
        riley = save("reply.riley@incidentiq.test", "Riley Reporter", UserRole.USER);
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    private ResultActions create(UserAccount as, String title, String body, boolean personal) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(post("/api/saved-replies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"%s\", \"body\": \"%s\", \"personal\": %s}"
                        .formatted(title, body, personal)));
    }

    private String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions list(UserAccount as) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(get("/api/saved-replies"));
    }

    private ResultActions update(UserAccount as, String id, String title) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(put("/api/saved-replies/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"" + title + "\", \"body\": \"New text\"}"));
    }

    private ResultActions remove(UserAccount as, String id) throws Exception {
        TestAuth.signInAs(as);
        return mockMvc.perform(delete("/api/saved-replies/{id}", id));
    }

    @Test
    void adminsShareRepliesWithTheTeam() throws Exception {
        String restart = id(create(admin, "  Restart  ", "Hi {reporter}, please restart.", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Restart"))
                .andExpect(jsonPath("$.shared").value(true))
                .andExpect(jsonPath("$.canEdit").value(true)));

        list(alex)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Hi {reporter}, please restart."))
                .andExpect(jsonPath("$[0].canEdit").value(false));

        // Agents can't add, change or delete shared replies.
        create(alex, "Mine", "Text", false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Only admins can add shared replies. Add it as a personal reply instead."));
        update(alex, restart, "Hijack").andExpect(status().isForbidden());
        remove(alex, restart).andExpect(status().isForbidden());

        update(admin, restart, "Restart first").andExpect(jsonPath("$.title").value("Restart first"));
        remove(admin, restart).andExpect(status().isNoContent());
        list(alex).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void personalRepliesAreOnlyForTheirOwner() throws Exception {
        create(admin, "Escalated", "Escalated to networking.", false);
        String mine = id(create(alex, "Screenshot", "Can you send a screenshot?", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shared").value(false)));

        // Shared first, then personal.
        list(alex).andExpect(jsonPath("$[*].title", Matchers.contains("Escalated", "Screenshot")))
                .andExpect(jsonPath("$[1].canEdit").value(true));
        list(sam).andExpect(jsonPath("$[*].title", Matchers.contains("Escalated")));
        // Not even admins see someone else's personal replies.
        list(admin).andExpect(jsonPath("$[*].title", Matchers.contains("Escalated")));

        update(sam, mine, "Stolen").andExpect(status().isNotFound());
        remove(admin, mine).andExpect(status().isNotFound());
        update(alex, mine, "Screenshot please").andExpect(status().isOk());
        remove(alex, mine).andExpect(status().isNoContent());
    }

    @Test
    void reportersCantUseSavedReplies() throws Exception {
        create(admin, "Restart", "Please restart.", false);

        list(riley).andExpect(status().isForbidden());
        create(riley, "Mine", "Text", true).andExpect(status().isForbidden());
        TestAuth.signOut();
        mockMvc.perform(get("/api/saved-replies")).andExpect(status().isUnauthorized());
    }

    @Test
    void checksTheTitleAndText() throws Exception {
        create(alex, " ", "Text", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").value("Title cannot be empty"));
        create(alex, "x".repeat(101), "Text", true).andExpect(status().isBadRequest());
        create(alex, "Title", "x".repeat(2001), true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.body").value("Text must be at most 2000 characters"));
        update(alex, java.util.UUID.randomUUID().toString(), "Missing").andExpect(status().isNotFound());
    }

    @Test
    void limitsHowManyPersonalRepliesSomeoneKeeps() throws Exception {
        for (int i = 0; i < SavedReplyService.MAX_PERSONAL; i++) {
            create(alex, "Reply " + i, "Text", true).andExpect(status().isCreated());
        }
        create(alex, "One more", "Text", true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can keep at most 50 personal replies."));
        // Sam's own limit is separate.
        create(sam, "Mine", "Text", true).andExpect(status().isCreated());
    }
}
