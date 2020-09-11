package Calibration.ust.hk;

import java.io.IOException;
import java.util.LinkedHashMap;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.AnaModelCalibrationModule;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class SimRunImplToy implements SimRun{

	private final int lastIteration;
	private Measurements outputMeasurements;
	
	public SimRunImplToy(int lastIter) {
		this.lastIteration=lastIter;
	}
	
	@Override
	public void run(AnalyticalModel sue, Config config, LinkedHashMap<String, Double> params, boolean generateOd,
			String threadNo, MeasurementsStorage storage) {
		
		config.controler().setLastIteration(this.lastIteration);
		config.controler().setOutputDirectory("toyScenario/output"+threadNo);
		config.transit().setUseTransit(true);
		config.plansCalcRoute().setInsertingAccessEgressWalk(false);
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		config.global().setCoordinateSystem("arbitrary");
		config.parallelEventHandling().setNumberOfThreads(3);
		config.controler().setWritePlansInterval(50);
		config.qsim().setStartTime(0.0);
		config.qsim().setEndTime(93600.0);
		config.global().setNumberOfThreads(3);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.8);
		config.controler().setWriteEventsInterval(50);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		try {
			controler.addOverridingModule(new DynamicRoutingModuleV2(transitGenerator.createBusFareCalculator(scenario.getTransitSchedule()),"src/main/resources/toyScenarioData/Mtr_fare.csv",
					null, "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
		} catch (IOException e) {
		
			e.printStackTrace();
		}
		
		
		AnaModelCalibrationModule anaModule=new AnaModelCalibrationModule(storage, sue,"src/main/resources/toyScenarioData/Calibration/",params,true);
		this.outputMeasurements=anaModule.getOutputMeasurements();
		controler.addOverridingModule(anaModule);
		controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		controler.run();
	}

	@Override
	public Measurements getOutputMeasurements() {
		
		return this.outputMeasurements;
	}
}
