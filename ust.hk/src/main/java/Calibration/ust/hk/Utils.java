package Calibration.ust.hk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.core.config.Config;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.calibrator.ObjectiveCalculator;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class Utils {
	public static void calcMultiObjectiveFromResult(String realMeasurementsLoc, String resultFolderLoc, String outFileLoc) {
		Measurements realMeasurements = new MeasurementsReader().readMeasurements(realMeasurementsLoc);
		try {
			FileWriter fw = new FileWriter(new File(outFileLoc+"/multiObj.csv"));
			List<MeasurementType> types = new ArrayList<>();
		for(int i=0;i<50;i++) {
			if(!new File(resultFolderLoc+"/simMeasurement"+i+".xml").exists())return;
			Measurements m = new MeasurementsReader().readMeasurements(resultFolderLoc+"/simMeasurement"+i+".xml");
			Map<MeasurementType,Double> obj = ObjectiveCalculator.calcMultiObjective(realMeasurements, m, ObjectiveCalculator.TypeMeasurementAndTimeSpecific);
			
			if(i==0) {
				fw.append("Interation,");
				String s = "";
				for(MeasurementType t:obj.keySet()) {
					fw.append(s+t.toString());
					s = ",";
					types.add(t);
				}
				fw.append("\n");
			}
			String s = "";
			fw.append(i+",");
			for(MeasurementType t:types) {
				fw.append(s+obj.get(t));
				s = ",";
			}
			fw.append("\n");
		}
		fw.flush();
		fw.close();
	
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}
	
	public static void calcSeperatelyCalculatedMultiObjective(String resultCSVFileLoc,String outputFileLoc) {
		try {
			BufferedReader bf = new BufferedReader(new FileReader(new File(resultCSVFileLoc)));
			bf.readLine();//get rid of the header.
			Map<String,Tuple<Double,Double>> params = new HashMap<>();
			String line = null;
			while((line = bf.readLine())!=null) {
				String[] part = line.split(",");
				params.put(part[0], new Tuple<>(Double.parseDouble(part[1]),Double.parseDouble(part[2])));
			}
			bf.close();
			
			Measurements real=new MeasurementsReader().readMeasurements("src/main/resources/toyScenarioData/toyScenarioMeasurementVersion12_1.xml");
			
			Config initialConfig=ConfigGenerator.generateToyConfig();
			ParamReader pReader=new ParamReader("src/main/resources/toyScenarioData/paramReaderToy.csv");
			MeasurementsStorage storage=new MeasurementsStorage(real);
			FileWriter fw = new FileWriter(new File(outputFileLoc+"/multiObj.csv"));
			fw.append("key");
			List<MeasurementType> keys = new ArrayList<>();
			for(MeasurementType ss : real.getMeasurementsByType().keySet()) {
				fw.append(","+ss.toString());
				keys.add(ss);
			}
			fw.append("\n");
			for(Entry<String, Tuple<Double, Double>> d: params.entrySet()) {
				LinkedHashMap<String,Double>initialParams=CalibrationRunToy.loadInitialParam(pReader,new double[] {d.getValue().getFirst(),d.getValue().getSecond()});
				pReader.setInitialParam(initialParams);
				SimRun simRun=new SimRunImplToy(150);
				Config config=pReader.SetParamToConfig(initialConfig, initialParams);
				AnalyticalModel sue=new CNLSUEModel(real.getTimeBean());
				sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
				sue.setFileLoc(outputFileLoc);
				simRun.run(sue, config, initialParams, true, d.getKey(), storage);
				Measurements simM = storage.getSimMeasurement(initialParams);
				new MeasurementsWriter(simM).write(outputFileLoc+"/"+d.getKey()+".xml");
				Map<MeasurementType,Double>obj =  ObjectiveCalculator.calcMultiObjective(real, simM, ObjectiveCalculator.TypeMeasurementAndTimeSpecific);
				fw.append(d.getKey().toString());
				for(MeasurementType ss:keys) {
					if(obj.get(ss)!=null) {
						fw.append(","+obj.get(ss));
					}else {
						fw.append(","+0);
					}
				}
				fw.append("\n");
				fw.flush();
			}
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
//		String realMeasurement = "src/main/resources/toyScenarioData/toyScenarioMeasurementVersion12_1.xml";
//		String parentResultFileLoc = "toyScenario/multiObj";
//		List<String> folderLoc = new ArrayList<>();
//		File directory = new File(parentResultFileLoc);
//
//	    // Get all files from a directory.
//	    File[] fList = directory.listFiles();
//	    if(fList != null) {
//	        for (File file : fList) {      
//	            if (file.isDirectory()) {
//	                folderLoc.add(file.getPath());
//	            }
//	        }
//	    }
//		for(String s:folderLoc) {
//			calcMultiObjectiveFromResult(realMeasurement,s,s);
//		}
		
		String csvFileLoc = "toyScenario/multiObj/seperateObj.csv";
		String outputFileLoc  = "toyScenario/multiObj";
		calcSeperatelyCalculatedMultiObjective(csvFileLoc,outputFileLoc);
		
	}
}
