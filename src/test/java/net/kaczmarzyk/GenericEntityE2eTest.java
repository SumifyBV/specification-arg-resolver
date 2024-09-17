/**
 * Copyright 2014-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kaczmarzyk;

import net.kaczmarzyk.spring.data.jpa.GenericPerson;
import net.kaczmarzyk.spring.data.jpa.GenericPersonRepository;
import net.kaczmarzyk.spring.data.jpa.IntegrationTestBase;
import net.kaczmarzyk.spring.data.jpa.domain.Between;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.EqualIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import net.kaczmarzyk.utils.interceptor.HibernateStatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

import static net.kaczmarzyk.spring.data.jpa.web.annotation.OnTypeMismatch.EXCEPTION;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class GenericEntityE2eTest extends IntegrationTestBase {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private GenericPerson incognito;

    @And({
            @Spec(path = "name", spec = EqualIgnoreCase.class, onTypeMismatch = EXCEPTION),
            @Spec(path = "id", spec = Equal.class, onTypeMismatch = EXCEPTION),
            @Spec(path = "generic", params = {"start", "end"}, spec = Between.class, onTypeMismatch = EXCEPTION),
            @Spec(path = "anotherGeneric", spec = In.class, onTypeMismatch = EXCEPTION)
    })
    public interface GenericPersonSpec extends Specification<GenericPerson> {
    }

    @RestController
    public static class GenericPersonController {
        @Autowired
        GenericPersonRepository repository;

        @GetMapping("/generic-persons")
        public Page<GenericPerson> findGenericPersons(GenericEntityE2eTest.GenericPersonSpec spec, @PageableDefault Pageable pageable) {
            return repository.findAll(spec, pageable);
        }
    }

    @BeforeEach
    public void persistTestData() {
        em.persist(new GenericPerson("Ima Person", 1L));
        em.persist(new GenericPerson("Norma L. Citizen", 2L));
        em.persist(new GenericPerson("John Doe", 3L));
        em.persist(new GenericPerson("A.Nonymouse", 4L));
        em.persist(new GenericPerson("Ord N. Ary", 5L));
        em.persist(new GenericPerson("N. O. Body", 6L));
        incognito = new GenericPerson("I. N. Cognito", 7L);
        em.persist(incognito);
        em.flush();
        HibernateStatementInspector.clearInterceptedStatements();
    }

    @Test
    public void filtersById() throws Exception {
        var uuid = incognito.getId();
        mockMvc.perform(get("/generic-persons")
                        .param("id", uuid.toString())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("I. N. Cognito"));
    }

    @Test
    public void filtersByName() throws Exception {
        mockMvc.perform(get("/generic-persons")
                        .param("name", "John Doe")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.name == 'John Doe')]").exists());
    }

    @Test
    public void filtersByGeneric() throws Exception {
        mockMvc.perform(get("/generic-persons")
                        .param("start", "4")
                        .param("end", "10000")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(7)))
                .andExpect(jsonPath("$.content[?(@.name == 'Ima Person')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'Norma L. Citizen')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'John Doe')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'A.Nonymouse')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'Ord N. Ary')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'N. O. Body')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'I. N. Cognito')]").exists());
    }

    @Test
    public void filtersByAnotherGeneric() throws Exception {
        mockMvc.perform(get("/generic-persons")
                        .param("anotherGeneric", "1", "4", "7")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[?(@.name == 'Ima Person')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'A.Nonymouse')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'I. N. Cognito')]").exists());
    }
}
