package com.study.spring;

import com.study.spring.board.Board;
import com.study.spring.board.BoardRepository;
import com.study.spring.security.SiteUser;
import com.study.spring.security.SiteUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:board-regression;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "app.firebase.enabled=false",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ApplicationRegressionTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    BoardRepository boards;
    @Autowired
    SiteUserRepository users;
    @Autowired
    PasswordEncoder encoder;

    private Board addBoard(String title) {
        return boards.saveAndFlush(Board.builder()
                .title(title).content("test content").writer("tester").build());
    }

    @Test
    void publicPagesAndBootstrapAreAvailable() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/jpa/boards"))
                .andExpect(status().isOk())
                .andExpect(view().name("jpa/list"));
        mvc.perform(get("/webjars/bootstrap/css/bootstrap.min.css"))
                .andExpect(status().isOk());
    }

    @Test
    void searchKeepsKeywordAndReturnsSecondPage() throws Exception {
        for (int i = 0; i < 6; i++)
            addBoard("Spring guide " + i);
        addBoard("Other topic");
        MvcResult result = mvc.perform(get("/jpa/boards")
                .param("keyword", "Spring").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", "Spring"))
                .andExpect(model().attributeExists("startPage", "endPage"))
                .andExpect(content().string(containsString("keyword=Spring")))
                .andReturn();
        Page<?> page = (Page<?>) result.getModelAndView().getModel().get("boardPage");
        assertThat(page.getTotalElements()).isEqualTo(6);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumberOfElements()).isEqualTo(1);
    }

    @Test
    void emptySearchCanRender() throws Exception {
        MvcResult result = mvc.perform(get("/jpa/boards").param("keyword", "missing"))
                .andExpect(status().isOk()).andReturn();
        Page<?> page = (Page<?>) result.getModelAndView().getModel().get("boardPage");
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void formCrudWorks() throws Exception {
        mvc.perform(post("/jpa/boards").with(user("writer")).with(csrf())
                .param("title", "New post").param("content", "Body")
                .param("writer", "writer"))
                .andExpect(redirectedUrl("/jpa/boards"));
        Board saved = boards.findAll().getFirst();
        Long id = saved.getId();
        mvc.perform(get("/jpa/boards/{id}", id)).andExpect(status().isOk());
        mvc.perform(post("/jpa/boards/{id}/edit", id)
                .with(user("writer")).with(csrf())
                .param("title", "Changed").param("content", "Changed body")
                .param("writer", "writer"))
                .andExpect(redirectedUrl("/jpa/boards/" + id));
        boards.flush();
        assertThat(boards.findById(id).orElseThrow().getTitle()).isEqualTo("Changed");
        mvc.perform(post("/jpa/boards/{id}/delete", id)
                .with(user("writer")).with(csrf()))
                .andExpect(redirectedUrl("/jpa/boards"));
        assertThat(boards.existsById(id)).isFalse();
    }

    @Test
    void invalidEditKeepsTargetId() throws Exception {
        Long id = addBoard("Original").getId();
        mvc.perform(post("/jpa/boards/{id}/edit", id)
                .with(user("writer")).with(csrf())
                .param("title", "").param("content", "Body").param("writer", "writer"))
                .andExpect(status().isOk())
                .andExpect(view().name("jpa/form"))
                .andExpect(model().attributeHasFieldErrors("boardForm", "title"))
                .andExpect(model().attribute("boardId", id));
        assertThat(boards.findById(id).orElseThrow().getTitle()).isEqualTo("Original");
    }

    @Test
    void csrfIsRequiredForFormWrites() throws Exception {
        mvc.perform(post("/jpa/boards").with(user("writer"))
                .param("title", "New").param("content", "Body").param("writer", "writer"))
                .andExpect(status().isForbidden());
        assertThat(boards.count()).isZero();
    }

    @Test
    void adminPagesRequireAdminRole() throws Exception {
        mvc.perform(get("/admin/etc").with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/etc").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/firebase").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/jpa/boards/new").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void databaseLoginChecksPassword() throws Exception {
        users.saveAndFlush(new SiteUser("login-test", encoder.encode("test-password"), "USER"));
        mvc.perform(formLogin("/login").user("login-test").password("test-password"))
                .andExpect(authenticated().withUsername("login-test"));
        mvc.perform(formLogin("/login").user("login-test").password("wrong"))
                .andExpect(unauthenticated());
    }

    @Test
    void apiCrudAndPagingWork() throws Exception {
        String body = """
                {"title":"API post","content":"Body","writer":"api-user"}
                """;
        mvc.perform(post("/api/boards").with(user("api-user")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        Long id = boards.findAll().getFirst().getId();
        mvc.perform(get("/api/boards").param("keyword", "API").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.boards[0].title").value("API post"));
        mvc.perform(put("/api/boards/{id}", id).with(user("api-user")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("API post", "API changed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("API changed"));
        mvc.perform(delete("/api/boards/{id}", id).with(user("api-user")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/boards/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void firebaseIsProtectedAndDisabledDuringTests() throws Exception {
        mvc.perform(get("/api/firebase/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/firebase/status").with(user("reader")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/firebase/status").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        String message = "{\"name\":\"tester\",\"message\":\"hello\"}";
        mvc.perform(post("/api/firebase/messages").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/firebase/messages").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isServiceUnavailable());
    }
}
