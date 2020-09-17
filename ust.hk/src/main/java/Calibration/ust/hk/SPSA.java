package Calibration.ust.hk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Random;

import org.matsim.core.config.Config;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.calibrator.ObjectiveCalculator;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class SPSA {
	
	public static OptimizationResult runSPSA(int n, LinkedHashMap<String,Double> thetaPure, Evaluator evaluator, 
			double p, double a, double A, double c, double alpha, double gamma, int maxFunctionEval, String fileLoc) throws IOException {
		int functionEvaluationCounter=0;
		
		FileWriter fw = new FileWriter(new File(fileLoc));
		
		fw.append("Itearation No,CurrentObjective, EvalNo");
		
		LinkedHashMap<String,Double> theta = new LinkedHashMap<>();
		for(String s:thetaPure.keySet()) {
			theta.put(s, 0.);
			fw.append(","+s);
		}
		fw.append(",gradient\n");
		
		fw.flush();
		
		
		//should decrease
		double[] ak = new double[theta.size()];
		double[] ck = new double[theta.size()];
		
		double curF = 0;
		for(int k=1; k<=n; k++) {			
			fw.append(k+",");
			System.out.println("Iteration "+k);
			for(int i=0; i<theta.size(); i++) { // update ak, ck
				ak[i] = a / Math.pow((k+a+A), alpha);
				System.out.println("\tak["+i+"]="+ak[i]);
				ck[i] = c / Math.pow(k+1, gamma);
				System.out.println("\tck["+i+"]="+ck[i]);
			}
			
			curF = evaluator.evaluate( scaleFromPercentage(thetaPure,theta), k, 0);
			fw.append(curF+",");
			//functionEvaluationCounter++;
			fw.append(Integer.toString(functionEvaluationCounter+2));
			
			//Add a listener
			double[] delta = generateBernouilliDistribution(theta.size());
		
			LinkedHashMap<String,Double> thetaMinus = new LinkedHashMap<>();
			LinkedHashMap<String,Double> thetaPlus = new LinkedHashMap<>();
			int i=0;
			for(String s:theta.keySet()) {
				thetaMinus.put(s,theta.get(s)-c*delta[i]); //delta in {-1, 1}
				thetaPlus.put(s,theta.get(s)+c*delta[i]);
				i++;
				fw.append(","+theta.get(s));
			}
			
			
			double yplus = evaluator.evaluate(scaleFromPercentage(thetaPure,thetaPlus),k,1);
			functionEvaluationCounter++;
			double yminus = evaluator.evaluate(scaleFromPercentage(thetaPure,thetaMinus),k,2);
			functionEvaluationCounter++;
			
			
			double[] ghat = new double[theta.size()];
			if(Math.abs(yplus-yminus)<0.05) //Appropriate termination criteria?
				return new OptimizationResult(curF,scaleFromPercentage(thetaPure,theta));
			i=0;
			for(String s:theta.keySet()) {
				ghat[i] = (yplus-yminus) / 2*ck[i]*delta[i];
				fw.append(","+ghat[i]);
//				theta[i] = theta[i] - ak[i]*ghat[i];
				double newValue = theta.get(s) - ak[i]*ghat[i];
				if(!Double.isInfinite(newValue) && !Double.isNaN(newValue)) theta.put(s, newValue);
				System.out.println("\t "+i+": ghat="+ghat[i]+ "=> theta:="+theta.get(s));
				i++;
			}
			System.out.println("---next iteration?");
			fw.append("\n");
			fw.flush();
//			br.readLine();
			
			if(functionEvaluationCounter >=maxFunctionEval) {
				break;
			}
			
		}
		fw.close();
		return new OptimizationResult(curF,scaleFromPercentage(thetaPure,theta)) ;
	}
	
	private static double[]  generateBernouilliDistribution(int size) {
		double[] delta = new double[size];
		Random rand = new Random();
		for(int i = 0; i<size; i++) {
			delta[i] = (2 * (Math.round(rand.nextDouble())))-1;
		}
		return delta;
	}
	
	//returns the percentage map
	private static LinkedHashMap<String,Double> scaleToPercentage(LinkedHashMap<String,Double> original, LinkedHashMap<String,Double> Changed) {
		LinkedHashMap<String,Double> percentagemap = new LinkedHashMap<String,Double>();
		for(Entry<String, Double> s: original.entrySet()) {
			double percentChange = (Changed.get(s.getKey())-s.getValue())/s.getValue()*100;
			percentagemap.put(s.getKey(),percentChange);
		}
		
		return percentagemap;
	}
	
	//returns the chaged map
	private static LinkedHashMap<String,Double> scaleFromPercentage(LinkedHashMap<String,Double> original,LinkedHashMap<String,Double>percentage) {
		LinkedHashMap<String,Double> changedMap = new LinkedHashMap<String,Double>();
		for(Entry<String, Double> s: original.entrySet()) {
			double actualChange = s.getValue()*(percentage.get(s.getKey())/100+1);
			changedMap.put(s.getKey(),actualChange);
		}
		
		return changedMap;
	}
	
	public static void main(String[] args) throws IOException {
		Measurements calibrationMeasurements=new MeasurementsReader().readMeasurements("src/main/resources/toyScenarioData/toyMeasurements_10thOct19.xml");
		Config initialConfig=ConfigGenerator.generateToyConfig();
		ParamReader pReader=new ParamReader("src/main/resources/toyScenarioData/paramReaderToy.csv");
		LinkedHashMap<String,Double>initialParams=loadInitialParam(pReader,new double[] {-40,.8});
		SimRun simRun=new SimRunImplToy(150);
		
		Evaluator evaluator = new Evaluator(simRun, calibrationMeasurements, pReader, initialConfig);
		
		double p = 0.5d; // not yet regarded
		double a = 1;
		double A = 0; 
		double c = 0.0005; 
		double alpha = 0.602d;//1
		double gamma = 0.101d;// 1/6
		
		OptimizationResult result= SPSA.runSPSA(50,initialParams, evaluator, p, a, A, c, alpha, gamma, 50,"toyScenario/SPSA/spsa_"+initialParams.values().toString()+".csv");
		
		
		
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
}

class OptimizationResult{
	public final double objValue;
	public final LinkedHashMap<String,Double> solution;
	
	public OptimizationResult(double objectiveValue, LinkedHashMap<String,Double> solution) {
		this.objValue = objectiveValue;
		this.solution = solution;
	}
	
}

class Evaluator{
	private SimRun simRun;
	private Measurements realMeasurements;
	private ParamReader pReader;
	private Config config;
	
	
	
	public SimRun getSimRun() {
		return simRun;
	}

	public Measurements getRealMeasurements() {
		return realMeasurements;
	}

	public ParamReader getpReader() {
		return pReader;
	}

	public Config getConfig() {
		return config;
	}

	public Evaluator(SimRun simRun, Measurements realMeasurements, ParamReader pReader, Config config) {
		this.simRun = simRun;
		this.realMeasurements = realMeasurements;
		this.pReader = pReader;
		this.config = config;
	}
	
	public Double evaluate(LinkedHashMap<String,Double> param,int currentIter,int gradIter) {
		MeasurementsStorage storage = new MeasurementsStorage(realMeasurements);
		AnalyticalModel sue=new CNLSUEModel(storage.getTimeBean());		
		sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
		
		Config config1=pReader.SetParamToConfig(config, param);
		simRun.run(sue, config1, param, true,currentIter+"_"+gradIter,storage);
		new MeasurementsWriter(storage.getSimMeasurement(param)).write("toyScenario/SPSA/"+currentIter+"_"+gradIter+".xml");
		return ObjectiveCalculator.calcObjective(realMeasurements, storage.getSimMeasurement(param), ObjectiveCalculator.TypeMeasurementAndTimeSpecific);
	}
	
	
	
	
	
}
