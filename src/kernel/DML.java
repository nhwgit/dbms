package kernel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dataStructure.Attribute;
import dataStructure.Table;
import dataStructure.Type;
import exception.ExceedingItemException;
import exception.InvalidSyntaxException;
import exception.InvalidTypeException;
import exception.NotAllowNullException;
import exception.UniqueKeyViolatonException;
import util.FileUtil;
import util.KernelUtil;

public class DML {
	public static void insertCommand(String cmd) {
		String [] item = cmd.trim().split("\n|\r\n");
		String [] header = item[0].trim().split("\\s+");
		String tableName = header[2];

		Table table = FileUtil.readObjectFromFile(new Table(), tableName+".bin");
		List<String> pKey = table.getPrimaryKey();
		List<Type> attributeType = new ArrayList();
		List<Attribute> attributes = table.getAttribute();

		for(Attribute attr:attributes) {
			attributeType.add(attr.getType());
		}
		String regex = "\\(([^)]+)\\)";
		Pattern pattern = Pattern.compile(regex);

		try(BufferedWriter br = new BufferedWriter(new FileWriter(tableName+".txt", true))) {
			for(int i=1; i<item.length; i++) {
				Matcher matcher = pattern.matcher(item[i]);
				String [] seperatedData;
				if(matcher.find())
					seperatedData = matcher.group(1).trim().split("[\\s,']+");
				else throw new InvalidSyntaxException();
				if(seperatedData.length > attributeType.size())
					throw new ExceedingItemException();
				for(int j=0; j<attributeType.size(); j++) {
					String data = seperatedData[j];
					Attribute attribute = attributes.get(j);
					Type type = attributeType.get(j);
					if(data != null) {
						if(KernelUtil.isPrimaryKey(pKey, attributes.get(j).getField())) {
							if(insertUniqueKeyCheck(data, j, tableName) ==false)
								throw new UniqueKeyViolatonException();
						}
						else {
							if(insertTypeCheck(type, data) == false)
								throw new InvalidTypeException();
						}
						br.write(data + "\t");
					}
					else {
						String typeName = type.getTypeName();
						boolean allowNull = attribute.getAllowNull();
						String nullValue = insertNullLogic(typeName, allowNull);
						br.write(nullValue + "\t");
						}
					}
					br.newLine();
				}
			} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public static String insertNullLogic(String typeName, boolean allowNull) {
		String ret = null;
		if(allowNull == true) {
			if(typeName.equalsIgnoreCase("INTEGER")) ret = "0";
			else if(typeName.equalsIgnoreCase("VARCHAR")) ret = "null";
		}
		else throw new NotAllowNullException();
		return ret;
	}

	public static boolean insertTypeCheck(Type type, String data) {
		if(type.getTypeName().equalsIgnoreCase("INTEGER")) {
			try {
				Integer.parseInt(data);
			}
			catch(NumberFormatException e) {
				return false;
			}
		}
		if(data.length() > type.getTypeSize()) return false;
		else return true;
	}

	public static boolean insertUniqueKeyCheck(String data, int idx, String tableName) {
		try(BufferedReader br = new BufferedReader(new FileReader(tableName+".txt"))) {
			br.readLine(); // 헤더 읽기
			String tuple;
			while(true) {
				tuple = br.readLine();
				if(tuple==null) break;
				String [] TupleParse= tuple.trim().split("\\s+");
				if(TupleParse[idx].equalsIgnoreCase(data)) return false;
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	public static void updateCommand(String cmd) {

	}

	public static void deleteCommand(String cmd) {

	}
}
