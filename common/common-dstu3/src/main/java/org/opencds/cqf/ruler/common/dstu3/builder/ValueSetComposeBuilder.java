package org.opencds.cqf.ruler.common.dstu3.builder;

import java.util.List;

import org.hl7.fhir.dstu3.model.ValueSet;
import org.opencds.cqf.ruler.common.builder.BaseBuilder;

public class ValueSetComposeBuilder extends BaseBuilder<ValueSet.ValueSetComposeComponent> {

    public ValueSetComposeBuilder(ValueSet.ValueSetComposeComponent complexProperty) {
        super(complexProperty);
    }

    public ValueSetComposeBuilder buildIncludes(List<ValueSet.ConceptSetComponent> includes) {
        complexProperty.setInclude(includes);
        return this;
    }

    public ValueSetComposeBuilder buildIncludes(ValueSet.ConceptSetComponent include) {
        complexProperty.addInclude(include);
        return this;
    }
}
