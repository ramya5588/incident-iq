package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class AttachmentApiTest {

    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R');
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    private UserAccount riley;
    private UserAccount sam;
    private UserAccount agent;
    private UserAccount otherAgent;
    private UserAccount admin;
    private String incidentId;

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @BeforeEach
    void setUp() throws Exception {
        riley = save("att.riley@incidentiq.test", "Riley Reporter", UserRole.USER);
        sam = save("att.sam@incidentiq.test", "Sam Someone", UserRole.USER);
        agent = save("att.agent@incidentiq.test", "Alex Agent", UserRole.AGENT);
        otherAgent = save("att.agent2@incidentiq.test", "Avery Agent", UserRole.AGENT);
        admin = save("att.admin@incidentiq.test", "Ada Admin", UserRole.ADMIN);

        TestAuth.signInAs(riley);
        String body = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Screen flickers", "description": "See screenshot",
                                 "priority": "LOW", "category": "Hardware"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        incidentId = JsonPath.read(body, "$.id");
    }

    @AfterEach
    void signOut() {
        TestAuth.signOut();
    }

    private UserAccount save(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(new UserAccount(email, name, role));
    }

    private ResultActions upload(UserAccount as, String name, byte[] data) throws Exception {
        TestAuth.signInAs(as);
        // The type the browser claims is deliberately misleading: it's ignored.
        return mockMvc.perform(multipart("/api/incidents/{id}/attachments", incidentId)
                .file(new MockMultipartFile("file", name, "image/png", data)));
    }

    private String uploadedId(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void reportersAttachScreenshotsToTheirIncidents() throws Exception {
        String id = uploadedId(upload(riley, "flicker.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("flicker.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.image").value(true))
                .andExpect(jsonPath("$.sizeBytes").value(PNG.length))
                .andExpect(jsonPath("$.uploadedByName").value("Riley Reporter")));

        mockMvc.perform(get("/api/incidents/{id}/attachments", incidentId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));

        // Images show inline, locked down so nothing in them can run.
        mockMvc.perform(get("/api/incidents/{id}/attachments/{aid}", incidentId, id))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("inline;")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("flicker.png")))
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("sandbox")))
                .andExpect(header().stringValues("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$[0].type").value("ATTACHMENT_ADDED"))
                .andExpect(jsonPath("$[0].toValue").value("flicker.png"))
                .andExpect(jsonPath("$[0].actorName").value("Riley Reporter"));
    }

    @Test
    void theTypeComesFromTheContentNotTheName() throws Exception {
        byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);
        upload(riley, "innocent.png", html)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.startsWith("That kind of file can't be attached.")));
        upload(riley, "page.html", html).andExpect(status().isBadRequest());
        upload(riley, "drawing.svg", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        upload(riley, "program.exe", bytes('M', 'Z', 0x90, 0)).andExpect(status().isBadRequest());
        // Binary content in a ".txt" is refused too.
        upload(riley, "dump.txt", bytes('a', 0, 'b')).andExpect(status().isBadRequest());

        // A text file full of HTML is fine, but only ever served as plain text, to download.
        String id = uploadedId(upload(riley, "app.log", html)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("text/plain;charset=UTF-8"))
                .andExpect(jsonPath("$.image").value(false)));
        mockMvc.perform(get("/api/incidents/{id}/attachments/{aid}", incidentId, id))
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")));

        String pdf = uploadedId(upload(riley, "report.pdf", PDF)
                .andExpect(jsonPath("$.contentType").value("application/pdf")));
        mockMvc.perform(get("/api/incidents/{id}/attachments/{aid}", incidentId, pdf))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")));
    }

    @Test
    void fileNamesAreCleaned() throws Exception {
        upload(riley, "..\\..\\windows\\system32\\evil.png", PNG)
                .andExpect(jsonPath("$.fileName").value("evil.png"));
        upload(riley, "../../etc/shot\u0007\".png", PNG)
                .andExpect(jsonPath("$.fileName").value("shot.png"));
        assertThat(AttachmentTypes.cleanFileName("a".repeat(300) + ".png")).hasSize(255).endsWith(".png");
        assertThat(AttachmentTypes.cleanFileName("  ")).isEqualTo("attachment");
        assertThat(AttachmentTypes.cleanFileName("..")).isEqualTo("attachment");
    }

    @Test
    void sizeAndCountAreLimited() throws Exception {
        byte[] tooBig = new byte[AttachmentService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);
        upload(riley, "huge.png", tooBig)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Files can be at most 5 MB."));
        upload(riley, "empty.png", new byte[0]).andExpect(status().isBadRequest());

        byte[] exactlyMax = Arrays.copyOf(PNG, AttachmentService.MAX_BYTES);
        upload(riley, "max.png", exactlyMax).andExpect(status().isCreated());
        for (int i = 1; i < AttachmentService.MAX_PER_INCIDENT; i++) {
            upload(riley, "shot" + i + ".png", PNG).andExpect(status().isCreated());
        }
        upload(riley, "one-too-many.png", PNG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "An incident can have at most 10 attachments. Remove one first."));
    }

    @Test
    void whoCanAttach() throws Exception {
        upload(sam, "not-mine.png", PNG)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only attach files to incidents you reported."));
        upload(agent, "from-agent.png", PNG).andExpect(status().isCreated());

        // Anyone signed in can see them, like the incident itself; nobody else can.
        TestAuth.signInAs(sam);
        mockMvc.perform(get("/api/incidents/{id}/attachments", incidentId))
                .andExpect(jsonPath("$.length()").value(1));
        TestAuth.signOut();
        mockMvc.perform(get("/api/incidents/{id}/attachments", incidentId)).andExpect(status().isUnauthorized());
    }

    @Test
    void whoCanRemove() throws Exception {
        String rileys = uploadedId(upload(riley, "rileys.png", PNG));
        String agents = uploadedId(upload(agent, "agents.png", PNG));

        TestAuth.signInAs(otherAgent);
        mockMvc.perform(delete("/api/incidents/{id}/attachments/{aid}", incidentId, rileys))
                .andExpect(status().isForbidden());

        TestAuth.signInAs(riley);
        mockMvc.perform(delete("/api/incidents/{id}/attachments/{aid}", incidentId, rileys))
                .andExpect(status().isNoContent());
        TestAuth.signInAs(admin);
        mockMvc.perform(delete("/api/incidents/{id}/attachments/{aid}", incidentId, agents))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/incidents/{id}/attachments/{aid}", incidentId, rileys))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/incidents/{id}/attachments", incidentId))
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/incidents/{id}/events", incidentId))
                .andExpect(jsonPath("$[0].type").value("ATTACHMENT_REMOVED"))
                .andExpect(jsonPath("$[0].toValue").value("agents.png"))
                .andExpect(jsonPath("$[0].actorName").value("Ada Admin"));
    }

    @Test
    void attachmentsBelongToTheirIncident() throws Exception {
        String id = uploadedId(upload(riley, "flicker.png", PNG));

        TestAuth.signInAs(riley);
        String other = JsonPath.read(mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Other\", \"description\": \"x\", \"priority\": \"LOW\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/incidents/{id}/attachments/{aid}", other, id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/incidents/{id}/attachments/{aid}", other, id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/incidents/{id}/attachments/{aid}", incidentId, id)).andExpect(status().isOk());
    }
}
