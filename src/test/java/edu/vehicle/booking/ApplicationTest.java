package edu.vehicle.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.UUID;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc class ApplicationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;

 @Test void serviceCatalogueIsPublic() throws Exception { mvc.perform(get("/api/services")).andExpect(status().isOk()); }

 @Test void profileRequiresAuthentication() throws Exception { mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()); }

 @Test void registrationValidatesPassword() throws Exception {
  mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
   .content("{\"fullName\":\"Test Customer\",\"email\":\"bad@example.com\",\"password\":\"short\"}"))
   .andExpect(status().isBadRequest());
 }

 @Test void customerCannotUseAdminApi() throws Exception {
  String token=registerCustomer();
  mvc.perform(get("/api/admin/bookings").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }

 @Test void customersCannotDeleteAnotherUsersVehicle() throws Exception {
  String firstToken=registerCustomer();
  MvcResult created=mvc.perform(post("/api/vehicles").header("Authorization","Bearer "+firstToken)
   .contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("registrationNumber","TEST-"+UUID.randomUUID().toString().substring(0,8),"make","Test","model","Car","year",2023,"fuelType","Petrol"))))
   .andExpect(status().isCreated()).andReturn();
  long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
  String otherToken=registerCustomer();
  mvc.perform(delete("/api/vehicles/"+id).header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
 }

 @Test void duplicateVehicleRegistrationReturnsHelpfulConflict() throws Exception {
  String token=registerCustomer();
  String registration="DUP-"+UUID.randomUUID().toString().substring(0,8);
  String body=json.writeValueAsString(Map.of("registrationNumber",registration,"make","Test","model","Car","year",2023,"fuelType","Petrol"));
  mvc.perform(post("/api/vehicles").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isCreated());
  mvc.perform(post("/api/vehicles").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isConflict()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("That registration number is already in use"));
 }

 private String registerCustomer() throws Exception {
  String email="customer-"+UUID.randomUUID()+"@example.com";
  MvcResult result=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(new RegisterRequest("Test Customer",email,"long-test-password",null))))
   .andExpect(status().isCreated()).andReturn();
  return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
 }
}
