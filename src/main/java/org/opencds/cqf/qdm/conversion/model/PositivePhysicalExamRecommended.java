package org.opencds.cqf.qdm.conversion.model;

import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.hl7.fhir.dstu3.model.ResourceType;

@ResourceDef(name="PositivePhysicalExamRecommended", profile="TODO")
public class PositivePhysicalExamRecommended extends PhysicalExamRecommended {
    @Override
    public PositivePhysicalExamRecommended copy() {
        PositivePhysicalExamRecommended retVal = new PositivePhysicalExamRecommended();
        super.copyValues(retVal);

        retVal.authorDatetime = authorDatetime;
        retVal.reason = reason;
        retVal.method = method;
        retVal.anatomicalLocationSite = anatomicalLocationSite;
        retVal.negationRationale = negationRationale;

        return retVal;
    }

    @Override
    public ResourceType getResourceType() {
        return null;
    }

    @Override
    public String getResourceName() {
        return "PositivePhysicalExamRecommended";
    }
}