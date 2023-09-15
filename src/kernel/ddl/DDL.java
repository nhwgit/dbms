package kernel.ddl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import dataStructure.Pair;
import dataStructure.table.Attribute;
import dataStructure.table.ForeignKey;
import dataStructure.table.Table;
import dataStructure.table.Type;
import exception.DeRefAlreadyExistenceException;
import exception.DisableForeignKeyGenerateException;
import exception.DuplicatedNameException;
import exception.FileAlreadyExistenceException;
import exception.InvalidSyntaxException;
import exception.NotAllowAlterModifyException;
import exception.NotAllowForeignKeyDelete;
import exception.NotExistPrimaryKeyException;
import exception.NotSupportedTypeException;
import exception.TooManyPrimarykeyException;
import exception.WrongColumnNameException;
import util.FileUtil;
import util.KernelUtil;

public class DDL {
	Table originTable;
	String originTableName;
	List<String> originTablePKey;
	List<Attribute> originTableAttrs;

	public DDL() {
		originTable = null;
		originTableName = null;
		originTablePKey = new LinkedList<>();
		originTableAttrs = new LinkedList<>();
	}

	private void initDDL() {
		originTableName = originTable.getTableName();
		originTablePKey = originTable.getPrimaryKey();
		originTableAttrs = originTable.getAttribute();
	}

	private void registerTableInfo() {
		originTable.setTableName(originTableName);
		originTable.setPrimaryKey(originTablePKey);
		originTable.setAttributes(originTableAttrs);
		FileUtil.writeObjectToFile(originTable, originTableName + ".bin");
	}

	private void updateTableInfo() {
		originTable.setTableName(originTableName);
		FileUtil.writeObjectToFile(originTable, originTableName + ".bin");
	}

	public void createCommand(String cmd) {
		int headerSize = cmd.indexOf("(");
		String[] header = cmd.substring(0, headerSize).split(" ");
		String type = header[1].toUpperCase();
		originTableName = header[2];
		File table = new File(originTableName + ".bin");
		if (table.exists())
			throw new FileAlreadyExistenceException();
		switch (type) {
		case "TABLE":
			createTableLogic(cmd.substring(headerSize + 1), originTableName);
			registerTableInfo();
			try (FileWriter fr = new FileWriter(originTableName + ".txt")) {
				Iterator<Attribute> itr = originTableAttrs.iterator();
				while (itr.hasNext()) {
					Attribute attr = itr.next();
					fr.write(attr.getField() + '\t');
				}
				fr.write("\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case "INDEX":
			createIndexLogic();
			break;
		default:
			throw new NotSupportedTypeException();
		}
	}

	public void alterCommand(String cmd) {
		String[] item = cmd.trim().split("\\s+");

		String type = item[1];
		originTableName = item[2];

		StringBuilder actionBuilder = new StringBuilder();
		for (int i = 3; i < item.length; i++) {
			actionBuilder.append(item[i] + ' ');
		}

		String action = actionBuilder.toString();

		switch (type) {
		case "TABLE":
			alterTableLogic(action);
			updateTableInfo();
			break;
		case "INDEX":
			break;
		}
	}

	public void dropCommand(String cmd) {
		String[] item = cmd.trim().split("\\s+");
		String type = item[1];
		Path binFilePath = Paths.get(originTableName + ".bin");
		Path txtFilePath = Paths.get(originTableName + ".txt");
		switch (type) {
		case "TABLE":
			try {
				Files.delete(binFilePath);
				Files.delete(txtFilePath);
			} catch (NoSuchFileException e) {
				System.out.println("삭제하려는 파일이 없습니다.");
			} catch (IOException e) {
				e.printStackTrace();
			}
		case "INDEX":
		default:
			throw new NotSupportedTypeException();
		}
	}

	private void createTableLogic(String cmd, String tableName) {
		originTable = new Table();
		String[] columns = cmd.split(",");
		for (String column : columns)
			newColumnRegisterLogic(column);
	}

	private void alterTableLogic(String cmd) {
		int index = cmd.indexOf(" ");
		String type = cmd.substring(0, index).toUpperCase();
		originTable = FileUtil.readObjectFromFile(new Table(), originTableName+".bin");
		initDDL();
		switch (type) {
		case "ADD":
			alterAddLogic(cmd); // 칼럼 추가
			break;
		case "RENAME":
			String judge = cmd.substring(index + 1, index + 3);
			if (judge.equalsIgnoreCase("TO"))
				alterRenameToLogic(cmd); // 테이블명 변경
			else
				alterRenameLogic(cmd); // 칼럼명 변경
			break;
		case "MODIFY":
			alterModifyLogic(cmd); // 데이터타입 변경
			break;
		case "DROP":
			alterDropLogic(cmd); // 칼럼 삭제
			break;
		default:
			throw new InvalidSyntaxException();
		}
	}

	private void alterAddLogic(String cmd) {
		String[] cmdParse = cmd.trim().split("\\s+");
		StringBuilder tmp = new StringBuilder();
		for (int i = 2; i < cmdParse.length; i++)
			tmp.append(cmdParse[i] + ' ');
		String registerCmd = (tmp.toString()).trim();
		newColumnRegisterLogic(registerCmd);

		String addColumnName = cmdParse[2];
		String addColumnType = cmdParse[3];

		alterDataAddLogic(addColumnName, addColumnType);
	}

	private void alterRenameLogic(String cmd) {
		String[] cmdParse = cmd.trim().split("\\s+");
		String oldName = cmdParse[2];
		String newName = cmdParse[4];
		for (Attribute attr : originTableAttrs) {
			if (attr.getField().equals(oldName)) {
				attr.setField(newName);
				for (String pKey : originTablePKey) {
					if (pKey.equals(oldName))
						pKey = newName;
				}
				updateAlterRenameTable(attr, oldName, newName);
			}
		}
		alterDataRenameLogic(oldName, newName);
	}

	private void alterModifyLogic(String cmd) {
		String[] cmdParse = cmd.trim().split("\\s+");
		String columnName = cmdParse[2];
		String newType = cmdParse[3];
		Type type = null;

		// 테이블에 이미 데이터가 있을 경우 타입 변경 불허
		try (BufferedReader br = new BufferedReader(new FileReader(originTableName + ".txt"))) {
			br.readLine();
			if (br.readLine() != null)
				throw new NotAllowAlterModifyException();
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (Attribute attr : originTableAttrs) {
			if (attr.getField().equals(columnName)) {
				type = KernelUtil.typeGenerator(newType);
				attr.setType(type);
			}
		}
		if (type == null)
			throw new WrongColumnNameException();
	}

	private void alterDropLogic(String cmd) {
		String[] cmdParse = cmd.trim().split("\\s+");
		String columnName = cmdParse[2];
		Iterator<Attribute> itr = originTableAttrs.iterator();
		while (itr.hasNext()) {
			Attribute attr = itr.next();
			if (attr.getField().equals(columnName)) {
				if(attr.getDeRefsInfo().size()!=0) throw new NotAllowForeignKeyDelete();
				itr.remove();
				Iterator<String> itr2 = originTablePKey.iterator();
				while (itr2.hasNext()) {
					if (itr2.next().equals(columnName))
						originTablePKey = null;
				}
			}
		}
		alterDataDropLogic(columnName);
	}

	private void alterRenameToLogic(String cmd) { // 테이블 명 변경
		String[] cmdParse = cmd.trim().split("\\s+");
		String newName = cmdParse[2];
		String oldName = originTable.getTableName();

		try {
			Path oldFile = Paths.get(oldName + ".bin");
			Path newFile = Paths.get(newName + ".bin");
			Files.move(oldFile, newFile);

			Path oldTxtFile = Paths.get(oldName + ".txt");
			Path newTxtFile = Paths.get(newName + ".txt");
			Files.move(oldTxtFile, newTxtFile);

			if (originTablePKey.size() >= 1) { // 기본키가 존재할 때
				updateAlterRenameToTable(oldName, newName);
			}
			originTableName = newName;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void alterDataAddLogic(String newColumnName, String typeName) {
		try (BufferedReader br = new BufferedReader(new FileReader(originTableName + ".txt"));
				BufferedWriter bw = new BufferedWriter(new FileWriter(originTableName + "temp.txt"))) {
			String line = br.readLine();
			bw.write(line + newColumnName + System.lineSeparator());

			String nullData = null;
			if (typeName.equals("INTEGER"))
				nullData = "0";
			else if (typeName.equals("VARCHAR"))
				nullData = "NULL";
			while (true) {
				line = br.readLine();
				if (line == null)
					break;
				bw.write(line + nullData + System.lineSeparator());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileUtil.updateFile(originTableName);
	}

	private void alterDataRenameLogic(String oldName, String newName) {
		try (BufferedReader br = new BufferedReader(new FileReader(originTableName + ".txt"));
				BufferedWriter bw = new BufferedWriter(new FileWriter(originTableName + "temp.txt"))) {
			String header = br.readLine();
			String[] headerParse = header.trim().split("\\s+");

			for (String item : headerParse) {
				if (item.equalsIgnoreCase(oldName))
					bw.write(newName + '\t');
				else
					bw.write(item + '\t');
			}
			bw.write(System.lineSeparator());

			while (true) {
				String line = br.readLine();
				if (line == null)
					break;
				bw.write(line + System.lineSeparator());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileUtil.updateFile(originTableName);
	}

	private void alterDataDropLogic(String name) {
		try (BufferedReader br = new BufferedReader(new FileReader(originTableName + ".txt"));
				BufferedWriter bw = new BufferedWriter(new FileWriter(originTableName + "temp.txt"))) {

			String header = br.readLine();
			String[] headerParse = header.trim().split("\\s+");

			int dropIdx = -1;

			for (int i = 0; i < headerParse.length; i++) {
				String columnName = headerParse[i];
				if (columnName.equalsIgnoreCase(name))
					dropIdx = i;
				else
					bw.write(columnName + '\t');
			}

			bw.write(System.lineSeparator());
			while (true) {
				String line = br.readLine();
				if (line == null)
					break;
				String[] lineParse = line.trim().split("\\s+");
				for (int i = 0; i < lineParse.length; i++) {
					if (i == dropIdx)
						continue;
					bw.write(lineParse[i] + '\t');
				}
				bw.write(System.lineSeparator());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		FileUtil.updateFile(originTableName);
	}

	private void newColumnRegisterLogic(String cmd) {
		String[] item = cmd.trim().split("\\s+|\\);");
		String field = null;
		Type type = null;
		boolean allowNull = true;
		ForeignKey infoForeignKey = null;
		Attribute attribute = new Attribute();
		try {
			field = item[0];
			type = KernelUtil.typeGenerator(item[1]);

			for (int i = 2; i < item.length; i++) { // not null, pk, fk등
				if (i < item.length - 1) {
					String command = (item[i] + " " + item[i + 1]).toUpperCase().trim();
					switch (command) {
					case "NOT NULL":
						allowNull = false;
						i++;
						break;
					case "PRIMARY KEY":
						if (originTablePKey.size() >= 1)
							throw new DeRefAlreadyExistenceException();
						addPrimaryKey(field);
						i++;
						break;
					case "FOREIGN KEY":
						i += 2;
						if (item[i].equalsIgnoreCase("REFERENCES")) {
							i++;
							String refTableName = item[i];
							Table refTable = FileUtil.readObjectFromFile(new Table(), refTableName + ".bin");
							List<String> refColumnList = refTable.getPrimaryKey();
							String refColumn = null;
							if (refColumnList == null)
								throw new DisableForeignKeyGenerateException();
							else if (refColumnList.size() >= 2) {
								throw new TooManyPrimarykeyException();
							} else {
								refColumn = refColumnList.get(0);
							}
							String deleteRule = "RESTRICT";
							String updateRule = "RESTRICT";
							i++;
							// 아래 코드 중복된거 간결화 예정
							while (i < item.length - 1) {
								if ((item[i] + " " + item[i + 1]).equalsIgnoreCase("ON DELETE")) {
									i += 2;
									if (item[i].equalsIgnoreCase("CASCADE")) {
										deleteRule = "CASCADE";
										i++;
									} else if ((item[i] + " " + item[i + 1]).equalsIgnoreCase("SET NULL")) {
										deleteRule = "SET NULL";
										i += 2;
									} else
										throw new InvalidSyntaxException();
								} else if (i <= item.length
										&& (item[i] + " " + item[i + 1]).equalsIgnoreCase("ON UPDATE")) {
									i += 2;
									if (item[i].equalsIgnoreCase("CASCADE")) {
										updateRule = "CASCADE";
										i++;
									} else if ((item[i] + " " + item[i + 1]).equalsIgnoreCase("SET NULL")) {
										updateRule = "SET NULL";
										i += 2;
									} else
										throw new InvalidSyntaxException();
								} else
									break;
							}

							Pair<String, String> deRefTableContent = new Pair<>(originTableName,
									field);

							List<Integer> refTablePKeyIdx = refTable.getPrimaryKeyIdx();
							int refTablePKeyCount = refTablePKeyIdx.size();
							if (refTablePKeyCount > 1)
								throw new TooManyPrimarykeyException();
							else if (refTablePKeyCount == 0)
								throw new NotExistPrimaryKeyException();
							else { // 기본키가 하나인 경우 그 기본키를 참조하게(일단은 이렇게 구현함)
								int deRefInfoAddAttrIdx = refTablePKeyIdx.get(0);
								Attribute attr = originTableAttrs.get(deRefInfoAddAttrIdx);
								attr.addDeRefInfos(deRefTableContent);
							}
							FileUtil.writeObjectToFile(refTable, refTableName + ".bin");
							infoForeignKey = new ForeignKey(refTableName, refColumn, deleteRule, updateRule);
						}
						break;
					default:
						throw new InvalidSyntaxException();
					}
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
		attribute.setField(field);
		attribute.setType(type);
		attribute.setAllowNull(allowNull);
		attribute.setInfoForeignKey(infoForeignKey);

		insertAttribute(attribute);
	}

	private void insertAttribute(Attribute tuple) {
		String newField = tuple.getField();
		Iterator<Attribute> itr = originTableAttrs.iterator();
		while(itr.hasNext()) {
			String field = itr.next().getField();
			if(field.equals(newField)) throw new DuplicatedNameException();
		}

		originTableAttrs.add(tuple);
	}

	private void addPrimaryKey(String pri) {
		Iterator<String> itr = originTablePKey.iterator();
		while(itr.hasNext()) {
			String name = itr.next();
			if(name.equals(pri)) throw new DuplicatedNameException();
		}
		originTablePKey.add(pri);
	}

	private void updateAlterRenameToTable(String oldName, String newName) {
		for (Attribute attr : originTableAttrs) {
			for (Pair<String, String> deRef : attr.getDeRefsInfo()) {
				String deRefTableName = deRef.first;
				Table deRefTable = FileUtil.readObjectFromFile(new Table(), deRefTableName + ".bin");
				// DeRef의 칼럼명 기반으로 순회
				List<Attribute> deRefAttrs = deRefTable.getAttribute();
				for (Attribute deRefAttr : deRefAttrs) {
					ForeignKey deRefTableForeignKey = deRefAttr.getInfoForeignKey();
					if (deRefTableForeignKey.getRefTable().equalsIgnoreCase(oldName))
						deRefTableForeignKey.setRefTable(newName);
				}
			}
		}
	}

	private void updateAlterRenameTable(Attribute attr, String oldName, String newName) {
		for (Pair<String, String> deRef : attr.getDeRefsInfo()) {
			String deRefTableName = deRef.first;
			Table deRefTable = FileUtil.readObjectFromFile(new Table(), deRefTableName + ".bin");
			// DeRef의 칼럼명 기반으로 순회
			List<Attribute> deRefAttrs = deRefTable.getAttribute();
			for (Attribute deRefAttr : deRefAttrs) {
				ForeignKey deRefTableForeignKey = deRefAttr.getInfoForeignKey();
				if (deRefTableForeignKey.getRefColumn().equalsIgnoreCase(oldName))
					deRefTableForeignKey.setRefColumn(newName);
			}
		}
	}

	private void createIndexLogic() {

	}
}