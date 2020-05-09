package org.opencds.cqf.r4.processors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.client.api.IGenericClient;

import org.hl7.fhir.r4.model.*;

import java.util.List;

import org.quartz.SchedulerException;
import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.hl7.fhir.r4.model.CarePlan.CarePlanStatus;
import org.opencds.cqf.common.helpers.ClientHelper;
import org.opencds.cqf.r4.execution.ICarePlanProcessor;
import org.opencds.cqf.r4.schedule.RulerScheduler;
import org.opencds.cqf.r4.schedule.TaskJob;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IAnyResource;


public class CarePlanProcessor implements ICarePlanProcessor<CarePlan> {

    private FhirContext fhirContext;
    private IFhirResourceDao<Endpoint> endpointDao;

    public CarePlanProcessor(FhirContext fhirContext, DaoRegistry registry) {
        this.fhirContext = fhirContext;
        this.endpointDao = registry.getResourceDao(Endpoint.class);
    }

    /*
        $execute Operation
        if we dont want to expose the operation we can just remove the provider, but still have the functionality
        This is similar to the cqf-tooling separation of Processing and Operations
    */
    //TODO: add dataEndpoint id parameter for grabbing an endpoint that already exists
    @Override
    public IAnyResource execute(CarePlan carePlan) {
        Endpoint dataEndpoint = endpointDao.read(new IdType("local-endpoint"));
        IGenericClient workFlowClient = ClientHelper.getClient(fhirContext, dataEndpoint);
        workFlowClient.update().resource(carePlan).execute();
        carePlan.setStatus(CarePlanStatus.ACTIVE);
        workFlowClient.update().resource(carePlan).execute();

        List<Resource> containedResources = carePlan.getContained();
        containedResources.forEach(resource -> forContained(carePlan, resource, workFlowClient));
        System.out.println("CarePlan executed. ");
        return carePlan;
    }
    public IAnyResource execute(CarePlan carePlan, Endpoint dataEndpoint) {
        endpointDao.update(dataEndpoint);
        //Save CarePlan to DB
        
        IGenericClient workFlowClient = ClientHelper.getClient(fhirContext, dataEndpoint);
        workFlowClient.update().resource(carePlan).execute();
        carePlan.setStatus(CarePlanStatus.ACTIVE);
        workFlowClient.update().resource(carePlan).execute();

        List<Resource> containedResources = carePlan.getContained();
        containedResources.forEach(resource -> forContained(carePlan, resource, workFlowClient));
        System.out.println("CarePlan executed. ");
        return carePlan;
    }

    private void forContained(CarePlan carePlan, Resource resource, IGenericClient workFlowClient) {
        resource.setId(resource.getIdElement().getIdPart().replaceAll("#", ""));
        switch (resource.fhirType()) {
            case "Task": 
                //schedule Tasks
                scheduleTask(carePlan, (Task)resource, workFlowClient); 
                //Save Tasks to DB 
                //HACK!
                if (((Task)resource).getStatus().equals(TaskStatus.DRAFT)) {
                    workFlowClient.update().resource((Task)resource).execute();
                }
                break;
            default : 
                throw new FHIRException("Unkown Fhir Resource. " + resource.getId());
        }

    }

    private void scheduleTask(CarePlan carePlan, Task task, IGenericClient workFlowClient) {
        String executableSystem = "http://aphl.org/fhir/ecr/CodeSystem/executable-task-types";
        Boolean isExecutable = false;
        for (Coding coding : task.getCode().getCoding()) {
            if (coding.getSystem().equals(executableSystem)) {
                isExecutable = true;
            }
        }
        if (isExecutable) {
            // if (task.getCode().)
            task.setStatus(TaskStatus.INPROGRESS);
            workFlowClient.update().resource(task).execute();
            // if (task.hasExtension()) {
            //     Extension taskTimingExtension = task.getExtensionByUrl("http://hl7.org/fhir/aphl/StructureDefinition/timing");
            //     if (taskTimingExtension != null) {
            //         System.out.println("timing extension found");
            //         Type taskTiming = taskTimingExtension.getValue();
            //         if (taskTiming instanceof Timing) {
            //             Timing timing = (Timing)taskTiming;
            //             System.out.println(timing.getRepeat().getPeriod());
            //             System.out.println(timing.getRepeat().getPeriodUnit());
            //         }
            //     }
            //     Extension taskOffsetExtension = task.getExtensionByUrl("http://hl7.org/fhir/aphl/StructureDefinition/offset");
            //     if (taskOffsetExtension != null) {
            //         System.out.println("offset extension found");
            //         System.out.println("timing extension found");
            //         Type taskOffset = taskOffsetExtension.getValue();
            //         if (taskOffset instanceof Duration) {
            //             Duration duration = (Duration)taskOffset;
            //             System.out.println(duration.getValue());
            //             System.out.println(duration.getUnit());
            //         }
            //     }
            // }

            TaskJob job = new TaskJob(task);
            job.setId("job-" + task.getId());

            try {
                RulerScheduler scheduler = new RulerScheduler(job, "TEST");

                scheduler.start();
            }catch (SchedulerException e){

                e.printStackTrace();
            }
        }
        else System.out.println("Task is not executable.");
    }

    /*
        $execute Operation
        if we dont want to expose the operation we can just remove the provider, but still have the functionality
        This is similar to the cqf-tooling separation of Processing and Operations
    */

}