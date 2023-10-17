package util;

public class IntegerUtil {
	public static boolean isInteger(String strValue) {
	    try {
	      Integer.parseInt(strValue);
	      	return true;
	    } catch (NumberFormatException ex) {
	    	return false;
	    }
	}
}
