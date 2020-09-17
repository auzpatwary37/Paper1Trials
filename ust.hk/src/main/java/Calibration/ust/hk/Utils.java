package Calibration.ust.hk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ust.hk.praisehk.metamodelcalibration.calibrator.ObjectiveCalculator;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;

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
				String s = "";
				for(MeasurementType t:obj.keySet()) {
					fw.append(s+t.toString());
					s = ",";
					types.add(t);
				}
				fw.append("\n");
			}
			String s = "";
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
}
