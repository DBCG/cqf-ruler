  
package org.opencds.cqf.common.factories;

import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.fhir.model.Dstu3FhirModelResolver;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.cqframework.cql.elm.execution.UsingDef;

import java.util.HashMap;
import java.util.Map;

import org.opencds.cqf.cql.service.factory.DataProviderFactory;

import org.apache.commons.lang3.tuple.Pair;
import org.opencds.cqf.common.helpers.ClientHelper;
import org.opencds.cqf.common.retrieve.JpaFhirRetrieveProvider;

import ca.uhn.fhir.context.FhirContext;

import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;

public class DefaultDataProviderFactory<Endpoint> implements DataProviderFactory {
    private DaoRegistry registry;
    private FhirContext fhirContext;
    private Map<String, Endpoint> endpointIndex;
    private String dataProviderUri;

    @SuppressWarnings("serial")
    private static final Map<String, String> shorthandMap = new HashMap<String, String>() {
        {
            put("FHIR", "http://hl7.org/fhir");
            put("QUICK", "http://hl7.org/fhir");
            put("QDM", "urn:healthit-gov:qdm:v5_4");
        }
    };

    public DefaultDataProviderFactory(DaoRegistry registry, FhirContext fhirContext, Map<String, Endpoint> endpointIndex, String dataProviderUri) {
        this.registry = registry;
        this.fhirContext = fhirContext;
        this.endpointIndex = endpointIndex;
        this.dataProviderUri = dataProviderUri;
    }

	@Override
	public Map<String, DataProvider> create(Map<String, Pair<String, String>> modelVersionsAndUrls,
			TerminologyProvider terminologyProvider) {
                // null = local database connection
        if (dataProviderUri == null) {
            return this.getProviders(modelVersionsAndUrls, terminologyProvider);
        }
        else {
            // fileuri = file connection
            // if (terminologyUri == fileuri *This already exists in the Service project* ) {
            //     throw new IllegalArgumentException("File Uri is not supported by this server.");
            // }
            // remoteuri = client connection
            // terminologyUri -> endpoint -> client -> provider
            if (endpointIndex != null || !endpointIndex.isEmpty()) {
                Endpoint endpoint = this.endpointIndex.get(dataProviderUri);
                IGenericClient client;
                if (endpoint instanceof org.hl7.fhir.r4.model.Endpoint) {
                client = ClientHelper.getClient(fhirContext, (org.hl7.fhir.r4.model.Endpoint)endpoint);
                }
                else 
                    client = ClientHelper.getClient(fhirContext, (org.hl7.fhir.dstu3.model.Endpoint)endpoint);
            }
            
            // This should be get Client Based Providers, but there are none right now
            return this.getProviders(modelVersionsAndUrls, terminologyProvider);
        }
        
	}

    Map<String, DataProvider> create(Map<VersionedIdentifier, Library> libraries, Map<String,String> modelUris, TerminologyProvider terminologyProvider) {
        Map<String, Pair<String, String>> versions = this.getVersions(libraries, modelUris);
        return this.getProviders(versions, terminologyProvider);
        
    }

    private Map<String, Pair<String, String>> getVersions(Map<VersionedIdentifier, Library> libraries,
            Map<String, String> modelUris) {
        Map<String, Pair<String, String>> versions = new HashMap<>();
        for (Map.Entry<String, String> modelUri : modelUris.entrySet()) {
            String uri = shorthandMap.containsKey(modelUri.getKey()) ? shorthandMap.get(modelUri.getKey())
                    : modelUri.getKey();

            String version = null;
            for (Library library : libraries.values()) {
                if (version != null) {
                    break;
                }

                if (library.getUsings() != null && library.getUsings().getDef() != null) {
                    for (UsingDef u : library.getUsings().getDef()) {
                        if (u.getUri().equals(uri)) {
                            version = u.getVersion();
                            break;
                        }
                    }
                }
            }

            if (version == null) {
                throw new IllegalArgumentException(
                        String.format("A uri was specified for %s but is not used.", modelUri.getKey()));
            }

            if (versions.containsKey(uri)) {
                if (!versions.get(uri).getKey().equals(version)) {
                    throw new IllegalArgumentException(String.format(
                            "Libraries are using multiple versions of %s. Only one version is supported at a time.",
                            modelUri.getKey()));
                }

            } else {
                versions.put(uri, Pair.of(version, modelUri.getValue()));
            }

        }

        return versions;
    }

    private Map<String, DataProvider> getProviders(Map<String, Pair<String, String>> versions,
            TerminologyProvider terminologyProvider) {
        Map<String, DataProvider> providers = new HashMap<>();
        for (Map.Entry<String, Pair<String, String>> m : versions.entrySet()) {
            providers.put(m.getKey(),
                    this.getProvider(m.getKey(), m.getValue().getLeft(), m.getValue().getRight(), terminologyProvider));
        }

        return providers;
    }

    private DataProvider getProvider(String model, String version, String uri,
            TerminologyProvider terminologyProvider) {
        switch (model) {
        case "http://hl7.org/fhir":
            return this.getFHIRJpaProvider(version, terminologyProvider);

        default:
            throw new IllegalArgumentException(String.format("Unknown data provider uri: %s", model));
        }
    }

    public DataProvider getFHIRJpaProvider(String version, TerminologyProvider terminologyProvider) {
        switch(version) {
            case "3.0.0":
                Dstu3FhirModelResolver dstu3ModelResolver = new Dstu3FhirModelResolver();
                JpaFhirRetrieveProvider dstu3JpaRetrieveProvider = new JpaFhirRetrieveProvider(this.registry, new SearchParameterResolver(this.fhirContext));
                dstu3JpaRetrieveProvider.setTerminologyProvider(terminologyProvider);
                dstu3JpaRetrieveProvider.setExpandValueSets(true);
                return new CompositeDataProvider(dstu3ModelResolver, dstu3JpaRetrieveProvider);
            case "4.0.0":
                R4FhirModelResolver r4ModelResolver = new R4FhirModelResolver();
                JpaFhirRetrieveProvider r4JpaRetrieveProvider = new JpaFhirRetrieveProvider(this.registry, new SearchParameterResolver(this.fhirContext));
                r4JpaRetrieveProvider.setTerminologyProvider(terminologyProvider);
                r4JpaRetrieveProvider.setExpandValueSets(true);
                return new CompositeDataProvider(r4ModelResolver, r4JpaRetrieveProvider);

            default:
                throw new IllegalArgumentException(String.format("Unknown FHIR data provider vesion: %s", version));
        }
    }
}