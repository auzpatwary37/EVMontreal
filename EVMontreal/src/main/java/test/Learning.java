package test;
import java.util.ArrayList;
import java.util.List;
	
	public class Learning {
		public static void main(String[] args) {
			List<Double> l = new ArrayList<>();
			l.add(0.1);
			l.add(0.2);
			l.add(0.3);
			l.add(0.4);
			l.add(0.5);
			System.out.println(arrayToString(l));
		}
		public static String arrayToString(List<Double> objects) {
			String out = "";
			String sep = "";
			for(Double d:objects) {
				out=out+sep+Double.toString(d);
				sep = " ";
			}
			
			return out;
		}	
	}
	
	

	
				
	

