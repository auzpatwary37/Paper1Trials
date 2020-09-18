package Calibration.ust.hk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.PropertyConfigurator;
import org.matsim.core.config.Config;


import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModelSubPop;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.SUEModelContTime;
import ust.hk.praisehk.metamodelcalibration.calibrator.Calibrator;
import ust.hk.praisehk.metamodelcalibration.calibrator.CalibratorImpl;
import ust.hk.praisehk.metamodelcalibration.calibrator.ListMeasurementTypeObjectiveCalibratorImp;
import ust.hk.praisehk.metamodelcalibration.calibrator.MultiObjectiveCalibratorImpl;
import ust.hk.praisehk.metamodelcalibration.calibrator.ObjectiveCalculator;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.SimAndAnalyticalGradientCalculator;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class CalibrationRunToy {

	public static void main(String[] args) {
		
		
		final boolean internalCalibration=false;
		
		
		//Measurements calibrationMeasurements=new MeasurementsReader().readMeasurements("src/main/resources/toyScenarioData/toyMeasurements_10thOct19.xml");
		Measurements calibrationMeasurements=new MeasurementsReader().readMeasurements("src/main/resources/toyScenarioData/toyScenarioMeasurementVersion12_1.xml");
		
		//calibrationMeasurements.removeMeasurementsByType(MeasurementType.linkVolume);
		//calibrationMeasurements.removeMeasurementsByType(MeasurementType.smartCardEntry);
		//calibrationMeasurements.removeMeasurementsByType(MeasurementType.smartCardEntryAndExit);
		List<MeasurementType> types = new ArrayList<>();
		types.add(MeasurementType.linkVolume);
		//types.add(MeasurementType.smartCardEntry);
		//types.add(MeasurementType.smartCardEntryAndExit);
		
		
		Config initialConfig=ConfigGenerator.generateToyConfig();
		ParamReader pReader=new ParamReader("src/main/resources/toyScenarioData/paramReaderToy.csv");
		MeasurementsStorage storage=new MeasurementsStorage(calibrationMeasurements);

		LinkedHashMap<String,Double>initialParams=loadInitialParam(pReader,new double[] {-50,0.6});

		//LinkedHashMap<String,Double>initialParams=loadInitialParam(pReader,new double[] {-50,-50});

		LinkedHashMap<String,Double>params=initialParams;
		pReader.setInitialParam(initialParams);
		
		//Calibrator calibrator=new MultiObjectiveCalibratorImpl(calibrationMeasurements,"toyScenario/Calibration/", pReader,10, 4);
		//Calibrator calibrator=new CalibratorImpl(calibrationMeasurements,"toyScenario/Calibration/",internalCalibration, pReader,10, 4);
		Calibrator calibrator = new ListMeasurementTypeObjectiveCalibratorImp(calibrationMeasurements,"toyScenario/Calibration/", internalCalibration, pReader,10, 4,types);
		calibrator.setMaxTrRadius(25.0);
	
		
		SimRun simRun=new SimRunImplToy(150);
		
		writeRunParam(calibrator, "toyScenario/Calibration/", params, pReader);
 		AnalyticalModel sue=new CNLSUEModel(calibrationMeasurements.getTimeBean());
		
		for(int i=0;i<50;i++) {
			Config config=pReader.SetParamToConfig(initialConfig, params);
			
			sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
			sue.setFileLoc("toyScenario/");
			simRun.run(sue, config, params, true, Integer.toString(i), storage);
			config.global().setNumberOfThreads(7);
//			Measurements toyMeasurement= sue.perFormSUE(initialParams,sue.getAnalyticalModelInternalParams(), null);
//			new MeasurementsWriter(toyMeasurement).write("toyMeasurements_7thOct2019.xml");
		
			SimAndAnalyticalGradientCalculator gradientFactory=new SimAndAnalyticalGradientCalculator(config, storage, simRun, calibrator.getTrRadius()/2/100, "FD", i, false, pReader);
			params=calibrator.generateNewParam(sue, storage.getSimMeasurement(params), gradientFactory, MetaModel.AnalyticalLinearMetaModelName);

			//Insert Gradient Calculator
			//SimAndAnalyticalGradientCalculator gradientFactory=new SimAndAnalyticalGradientCalculator(config, storage, simRun,calibrator.getTrRadius()/2/100, "FD", i, false, pReader);
			//params=calibrator.generateNewParam(sue, storage.getSimMeasurement(params), gradientFactory, MetaModel.GradientBased_III_MetaModelName);

			//params=calibrator.generateNewParam(sue, storage.getSimMeasurement(params), null, null, MetaModel.LinearMetaModelName);

						
		}
		
		
		
	}
	
	public static LinkedHashMap<String,Double> loadInitialParam(ParamReader pReader,double[] paramList) {
		LinkedHashMap<String,Double> initialParam=new LinkedHashMap<>(pReader.getInitialParam());
		if(initialParam.size()!=paramList.length) {
			throw new IllegalArgumentException("Dimension MissMatch! Could not load the param.");
		}
		int i=0;
		for(String s:initialParam.keySet()) {
			initialParam.put(s, paramList[i]);
			i++;
		}
		
		return initialParam;
	}
	
	public static void writeRunParam(Calibrator calibrator,String fileWriteLoc,LinkedHashMap<String,Double>params,ParamReader pReader) {
		try {
			FileWriter fw=new FileWriter(new File(fileWriteLoc+"RunParam.csv"),true);
			fw.append("ParameterName,initialParam ,upperLimit,lowerLimit\n");
			for(String s:params.keySet()) {
				fw.append(s+","+params.get(s)+","+pReader.getParamLimit().get(s).getFirst()+","+pReader.getParamLimit().get(s).getSecond()+"\n");
			}
			fw.append("initialTrustRegionRadius,"+calibrator.getTrRadius()+"\n");
			fw.append("TrInc,"+calibrator.getTrusRegionIncreamentRatio()+"\n");
			fw.append("TrDecreasing,"+calibrator.getTrustRegionDecreamentRatio()+"\n");
			fw.append("MaxTr,"+calibrator.getMaxTrRadius()+"\n");
			fw.append("MinTr,"+calibrator.getMinTrRadius()+"\n");
			fw.append("rou,"+calibrator.getThresholdErrorRatio()+"\n");
			fw.append("AnalyticalModelInternalParamterCalibration,"+calibrator.isShouldPerformInternalParamCalibration()+"\n");
			fw.append("MaxSuccesiveRejection,"+calibrator.getMaxSuccesiveRejection()+"\n");
			fw.append("MinMetaChangeReq,"+calibrator.getMinMetaParamChange()+"\n");
			fw.append("ObjectiveType,"+calibrator.getObjectiveType()+"\n");
			
			fw.append("StrtingTime,"+LocalDateTime.now().toString()+"\n");
			
			fw.flush();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
}
