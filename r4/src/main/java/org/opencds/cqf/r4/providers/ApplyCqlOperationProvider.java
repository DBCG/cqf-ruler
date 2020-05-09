package org.opencds.cqf.r4.providers;

import ca.uhn.fhir.context.FhirContext;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.common.factories.DefaultTerminologyProviderFactory;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.opencds.cqf.common.helpers.TranslatorHelper;
import org.opencds.cqf.cql.engine.execution.Context;
import org.opencds.cqf.cql.engine.runtime.DateTime;

import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;

import org.apache.commons.lang3.tuple.Pair;
import org.opencds.cqf.cql.service.Response;
import org.opencds.cqf.cql.service.Service;
import org.opencds.cqf.cql.service.factory.DataProviderFactory;
import org.opencds.cqf.cql.service.factory.TerminologyProviderFactory;

public class ApplyCqlOperationProvider {

    private DataProviderFactory dataProviderFactory;
    private IFhirResourceDao<Bundle> bundleDao;
    private TerminologyProvider localSystemTerminologyProvider;
    private FhirContext fhirContext;
    private CqlExecutionProvider cqlExecutionProvider;

    public ApplyCqlOperationProvider(DataProviderFactory dataProviderFactory, TerminologyProvider localSystemTerminologyProvider, IFhirResourceDao<Bundle> bundleDao, FhirContext fhirContext, CqlExecutionProvider cqlExecutionProvider) {
        this.dataProviderFactory = dataProviderFactory;
        this.bundleDao = bundleDao;
        this.localSystemTerminologyProvider = localSystemTerminologyProvider;
        this.fhirContext = fhirContext;
        this.cqlExecutionProvider = cqlExecutionProvider;
    }

    @Operation(name = "$apply-cql", type = Bundle.class)
    public Bundle apply(@IdParam IdType id, @OperationParam(name = "endpoint") Endpoint endpoint) throws FHIRException {
        Map<String, Endpoint> endpointIndex = new HashMap<String, Endpoint>();
        if(endpoint != null) {
            endpointIndex.put(endpoint.getAddress(), endpoint);
        }
        TerminologyProviderFactory terminologyProviderFactory = new DefaultTerminologyProviderFactory<Endpoint>(fhirContext, this.localSystemTerminologyProvider, endpointIndex);
        Bundle bundle = this.bundleDao.read(id);
        if (bundle == null) {
            throw new IllegalArgumentException("Could not find Bundle/" + id.getIdPart());
        }
        return applyCql(bundle, terminologyProviderFactory);
    }

    @Operation(name = "$apply-cql", type = Bundle.class)
    public Bundle apply(@OperationParam(name = "resourceBundle", min = 1, max = 1, type = Bundle.class) Bundle bundle, @OperationParam(name = "endpoint") Endpoint endpoint)
            throws FHIRException
    {
        Map<String, Endpoint> endpointIndex = new HashMap<String, Endpoint>();
        if(endpoint != null) {
            endpointIndex.put(endpoint.getAddress(), endpoint);
        }
        TerminologyProviderFactory terminologyProviderFactory = new DefaultTerminologyProviderFactory<Endpoint>(fhirContext, this.localSystemTerminologyProvider, endpointIndex);
        return applyCql(bundle, terminologyProviderFactory);
    }

    public Bundle applyCql(Bundle bundle, TerminologyProviderFactory terminologyProviderFactory) throws FHIRException {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.hasResource()) {
                applyCqlToResource(entry.getResource(), terminologyProviderFactory);
            }
        }

        return bundle;
    }

    public Resource applyCqlToResource(Resource resource, TerminologyProviderFactory terminologyProviderFactory) throws FHIRException {
        Library library;
        for (Property child : resource.children()) {
            for (Base base : child.getValues()) {
                if (base != null) {
                    AbstractMap.SimpleEntry<String, String> extensions = getExtension(base);
                    if (extensions != null) {
                        Object result = this.cqlExecutionProvider.evaluateInContext(resource, extensions.getValue(), "",  false);
                        if (extensions.getKey().equals("extension")) {
                            resource.setProperty(child.getName(), resolveType(result, base.fhirType()));
                        } else {
                            String type = base.getChildByName(extensions.getKey()).getTypeCode();
                            base.setProperty(extensions.getKey(), resolveType(result, type));
                        }
                    }
                }
            }
        }
        return resource;
    }

    private AbstractMap.SimpleEntry<String, String> getExtension(Base base) {
        for (Property child : base.children()) {
            for (Base childBase : child.getValues()) {
                if (childBase != null) {
                    if (((Element) childBase).hasExtension()) {
                        for (Extension extension : ((Element) childBase).getExtension()) {
                            if (extension.getUrl().equals("http://hl7.org/fhir/StructureDefinition/cqf-expression")) {
                                return new AbstractMap.SimpleEntry<>(child.getName(),
                                        ((Expression) extension.getValue()).getExpression());
                            }
                        }
                    } else if (childBase instanceof Extension) {
                        return new AbstractMap.SimpleEntry<>(child.getName(),
                                ((Expression) ((Extension) childBase).getValue()).getExpression());
                    }
                }
            }
        }
        return null;
    }

    private Base resolveType(Object source, String type) {
        if (source instanceof Integer) {
            return new IntegerType((Integer) source);
        } else if (source instanceof BigDecimal) {
            return new DecimalType((BigDecimal) source);
        } else if (source instanceof Boolean) {
            return new BooleanType().setValue((Boolean) source);
        } else if (source instanceof String) {
            return new StringType((String) source);
        } else if (source instanceof DateTime) {
            if (type.equals("dateTime")) {
                return new DateTimeType().setValue(Date.from(((DateTime) source).getDateTime().toInstant()));
            }
            if (type.equals("date")) {
                return new DateType().setValue(Date.from(((DateTime) source).getDateTime().toInstant()));
            }
        } else if (source instanceof org.opencds.cqf.cql.engine.runtime.Date) {
            if (type.equals("dateTime")) {
                return new DateTimeType()
                        .setValue(java.sql.Date.valueOf(((org.opencds.cqf.cql.engine.runtime.Date) source).getDate()));
            }
            if (type.equals("date")) {
                return new DateType()
                        .setValue(java.sql.Date.valueOf(((org.opencds.cqf.cql.engine.runtime.Date) source).getDate()));
            }
        }

        if (source instanceof Base) {
            return (Base) source;
        }

        throw new RuntimeException("Unable to resolve type: " + source.getClass().getSimpleName());
    }
}
