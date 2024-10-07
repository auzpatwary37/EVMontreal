package locationChoice;

import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.apache.commons.math.linear.MatrixUtils;
import org.apache.commons.math.linear.RealVector;



public class MapToArray<T>{
private final List<T>keySet;
private final String id;

public MapToArray(String id, Map<T,Double> inputMap) {
	this.id = id;
	this.keySet = new ArrayList<>(inputMap.keySet());
}

public MapToArray(String id, Set<T> inputSet) {
	this.id = id;
	this.keySet = new ArrayList<>(inputSet);
}

public MapToArray(String id, List<T> inputList) {
	this.id = id;
	this.keySet = new ArrayList<>(inputList);
}

public Map<T,Double> getMap(double[] matrix){
	Map<T,Double> out = new HashMap<>();
	for(int i = 0;i<this.keySet.size();i++) {
		out.put(this.keySet.get(i), matrix[i]);
	}
	return out;
}

public Map<T,Double> extractMap(double[] matrix, Set<T> key){
	Map<T,Double> out = new HashMap<>();
	for(int i = 0;i<this.keySet.size();i++) {
		if(key.contains(this.keySet.get(i)))out.put(this.keySet.get(i), matrix[i]);
	}
	return out;
}

public double[] getMatrix(Map<T,Double> map) {
//	if(map.size()!=this.keySet.size()) {
//		System.out.println("Keyset size = "+this.keySet.size()+" and map size = "+map.size());
//		throw new IllegalArgumentException("dimension mismatch!!!");
//	}
	double[] out = new double[keySet.size()];
	for(int i = 0;i<this.keySet.size();i++) {
		if( map.get(this.keySet.get(i))!=null) {
			out[i] = map.get(this.keySet.get(i));
		}
		
	}
	return out;
}

public List<T> getKeySet() {
	return keySet;
}

public String getId() {
	return id;
}

public RealVector getRealVector(Map<T,Double> map) {
	return MatrixUtils.createRealVector(this.getMatrix(map));
}

public void writeCSV(Map<String,double[]> mapToWrite,String fileLoc) {
	List<String> fieldKeys = new ArrayList<>(mapToWrite.keySet());
	try {
		FileWriter fw = new FileWriter(new File(fileLoc));
		//append header
		fw.append("Keys");
		for(String k:fieldKeys)fw.append(","+k.toString());
		fw.append("\n");
		fw.flush();
		for(int i = 0;i<this.keySet.size();i++) {
			fw.append(this.keySet.get(i).toString());
			for(String fk:fieldKeys)fw.append(","+mapToWrite.get(fk)[i]);
			fw.append("\n");
			fw.flush();
		}
		fw.close();
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
}


public static void main(String[] args) {
	String a = "ami\"";
	System.out.println(a.replace("\"", ""));
}
}
