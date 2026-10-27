package cz.uhk.ordinace.patient;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cz.uhk.ordinace.AbstractDbIT;

class PatientApiIT extends AbstractDbIT {

    @Autowired
    MockMvc mvc;

    private ResultActions create(String json, String confirm) throws Exception {
        return mvc.perform(post("/api/patients").param("confirmDuplicate", confirm)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createdPatientIsFoundByAccentFreePrefixAndByPhoneWithAreaCode() throws Exception {
        create("""
                {"firstName":"Zdeněk","lastName":"Žlutický","birthYear":1970,
                 "phone":"+420 602 333 444","insuranceId":"9999999901"}""", "false")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("602333444"));

        mvc.perform(get("/api/patients").param("query", "zlut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastName").value("Žlutický"));
        mvc.perform(get("/api/patients").param("query", "+420 602 333"))
                .andExpect(jsonPath("$[0].lastName").value("Žlutický"));
    }

    @Test
    void duplicateInsuranceIdReturns409WithLinkToExisting() throws Exception {
        String json = """
                {"firstName":"Alena","lastName":"Bartošová","birthYear":1960,
                 "phone":"603000111","insuranceId":"9999999902"}""";
        String body = create(json, "false").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int id = JsonPath.read(body, "$.id");

        create(json, "false").andExpect(status().isConflict()).andExpect(jsonPath("$.existingId").value(id));
    }

    @Test
    void patientWithoutInsuranceIdNeedsConfirmationToDuplicate() throws Exception {
        String json = """
                {"firstName":"Olga","lastName":"Cizincová","birthYear":1985,"phone":"604000222"}""";
        create(json, "false").andExpect(status().isCreated());

        create(json, "false").andExpect(status().isConflict());
        create(json, "true").andExpect(status().isCreated());
    }

    @Test
    void missingRequiredFieldIsBadRequest() throws Exception {
        create("{\"firstName\":\"Jan\"}", "false").andExpect(status().isBadRequest());
    }
}
