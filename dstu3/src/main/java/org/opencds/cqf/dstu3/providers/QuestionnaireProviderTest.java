package org.opencds.cqf.dstu3.providers;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.QuestionnaireResponse;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import ca.uhn.fhir.context.FhirContext;

import java.io.*;
import java.util.HashMap;
import java.util.List;


class QuestionnaireProviderTest{

    @Test
    public void testQuestionnaireProviderExtract() {
        try {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream("questionnaireResponses/QR.json");
            String qrStr = IOUtils.toString(in, Charsets.UTF_8);
            QuestionnaireResponse qrOut = new QuestionnaireResponse();
            FhirContext fhirContextDStu3 = FhirContext.forDstu3();
            qrOut = (QuestionnaireResponse) fhirContextDStu3.newJsonParser().parseResource(qrStr);

            QuestionnaireProvider questionnaireProvider = new QuestionnaireProvider(fhirContextDStu3);
            Bundle obsBundle = questionnaireProvider.extractObservationFromQuestionnaireResponse(qrOut);
            checkResults(obsBundle, qrOut);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkResults(Bundle obsBundle, QuestionnaireResponse qrOut){
        HashMap observationMap = extractObsValues(obsBundle);
        qrOut.getItem().forEach(item ->{
            String obsKey = item.getLinkId();
            assertNotNull(observationMap.get(item.getLinkId()));
            switch(item.getAnswer().get(0).getValue().fhirType()){
                case "string":
                    assertEquals(observationMap.get(item.getLinkId()), item.getAnswer().get(0).getValue().toString(), "LinkId " + item.getLinkId() + " did not process correctly");
                    break;
                case "Coding":
                    assertEquals(observationMap.get(item.getLinkId()), item.getAnswer().get(0).getValueCoding().getCode().toString(), "LinkId " + item.getLinkId() + " did not process correctly");
                    break;
                case "boolean":
                    assertEquals(observationMap.get(item.getLinkId()), item.getAnswer().get(0).getValueBooleanType().booleanValue(), "LinkId " + item.getLinkId() + " did not process correctly");
                    break;
            }
//            assertEquals(observationMap.get(item.getLinkId()), item."");

        });
        System.out.println("done with stuff");
    }

    private HashMap extractObsValues(Bundle obsBundle){
        HashMap obsMap = new HashMap<String, String>();
        obsBundle.getEntry().forEach(entry ->{
            Observation obs = (Observation) entry.getResource();
            String key = getLinkId(obs.getExtension());
            assertNotNull(obs.getValue(), "Observation with id : " + obs.getId() + " is null.");
            switch (obs.getValue().fhirType()) {
                case "string":
                    obsMap.put(key, obs.getValueStringType().getValue());
                    break;
                case "CodeableConcept":
                    obsMap.put(key, obs.getValueCodeableConcept().getCoding().get(0).getCode());
                    break;
                case "boolean":
                    obsMap.put(key, obs.getValueBooleanType().booleanValue());
                    break;
            }
        });
        return obsMap;
    }

    private String getLinkId(List<Extension> extensions){
        for (Extension extension : extensions) {
            if (extension.getUrl().equalsIgnoreCase("http://hl7.org/fhir/uv/sdc/StructureDefinition/derivedFromLinkId")) {
                return extension.getValueAsPrimitive().getValueAsString();
            }
        }
        return null;
    }
}