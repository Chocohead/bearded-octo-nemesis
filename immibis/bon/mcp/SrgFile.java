package immibis.bon.mcp;

import immibis.bon.Mapping;
import immibis.bon.NameSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SrgFile {
	
	public Map<String, String> classes = new HashMap<String, String>(); // name -> name
	public Map<String, String> fields = new HashMap<String, String>(); // owner/name -> name
	public Map<String, String> methods = new HashMap<String, String>(); // owner/namedesc -> name
	public boolean oldStyle = false;
	public Map<String, String> SRGtoMCPfields = new HashMap<String, String>(); // name -> name
	public Map<String, String> SRGtoMCPmethods = new HashMap<String, String>(); // name -> name
	
	public static String getLastComponent(String s) {
		String[] parts = s.split("/");
		return parts[parts.length - 1];
	}
	
	private SrgFile() {}
	
	/** Does not close <var>r</var>. */
	public static SrgFile read(Reader r, boolean reverse) throws IOException {
		@SuppressWarnings("resource")
		Scanner in = new Scanner(r);
		SrgFile rv = new SrgFile();
		while(in.hasNextLine()) {
			if(in.hasNext("CL:")) {
				in.next();
				String obf = in.next();
				String deobf = in.next();
				if(reverse)
					rv.classes.put(deobf, obf);
				else
					rv.classes.put(obf, deobf);
			} else if(in.hasNext("FD:")) {
				in.next();
				String obf = in.next();
				String deobf = in.next();
				if(reverse)
					rv.fields.put(deobf, getLastComponent(obf));
				else
					rv.fields.put(obf, getLastComponent(deobf));
			} else if(in.hasNext("MD:")) {
				in.next();
				String obf = in.next();
				String obfdesc = in.next();
				String deobf = in.next();
				String deobfdesc = in.next();
				if(reverse)
					rv.methods.put(deobf + deobfdesc, getLastComponent(obf));
				else
					rv.methods.put(obf + obfdesc, getLastComponent(deobf));
			} else {
				in.nextLine();
			}
		}
		return rv;
	}

	private static String stripQuotes(String s) {
		return s.substring(1, s.length() - 1);
	}

	public static SrgFile readOld(File mcpConfDir, int side, boolean reverse) throws IOException {
		SrgFile rv = new SrgFile();

		try (Scanner in = new Scanner(new BufferedReader(new FileReader(new File(mcpConfDir, "classes.csv"))))) {			
			//Lifted from CsvFile
			Map<String, String> data = new HashMap<>();

			in.useDelimiter(",");
			while(in.hasNextLine()) {
				String name = stripQuotes(in.next());
				String notch = stripQuotes(in.next());
				in.next(); //Skip supername
				String pack = stripQuotes(in.next());
				String sideNumber = in.nextLine().substring(2, 3); //Remove the last comma and surrounding quotes

				try {
					if (Integer.parseInt(sideNumber) == side) {
						if (reverse)
							data.put(pack + '/' + name, notch);
						else
							data.put(notch, pack + '/' + name);
					}
				} catch(NumberFormatException e) {
					//This fails on the header line
				}
			}
			rv.classes = data;
		}

		try (Scanner in = new Scanner(new BufferedReader(new FileReader(new File(mcpConfDir, "methods.csv"))))) {
			Map<String, String> NOTCHtoSRG = new HashMap<>();
			Map<String, String> SRGtoMCP = new HashMap<>();

			in.useDelimiter(",");
			while(in.hasNextLine()) {
				String SRGname = stripQuotes(in.next());
				String MCPname = stripQuotes(in.next());
				String NOTCHname = stripQuotes(in.next());
				String sig = stripQuotes(in.next());
				String obfSig = stripQuotes(in.next());
				String clazz = stripQuotes(in.next());
				String obfClazz = stripQuotes(in.next());
				String pack = stripQuotes(in.next());
				String sideNumber = in.nextLine().substring(2, 3); //Remove the last comma and surrounding quotes

				try {
					if (Integer.parseInt(sideNumber) == side) {
						if (reverse)
							NOTCHtoSRG.put(pack + '/' + clazz + '/' + SRGname + sig, NOTCHname);
						else
							NOTCHtoSRG.put(obfClazz + '/' + NOTCHname + obfSig, SRGname);
						SRGtoMCP.put(SRGname, MCPname);
					}
				} catch(NumberFormatException e) {
					//This fails on the header line
				}
			}
			rv.methods = NOTCHtoSRG;
			rv.SRGtoMCPmethods = SRGtoMCP;
		}

		try (Scanner in = new Scanner(new BufferedReader(new FileReader(new File(mcpConfDir, "fields.csv"))))) {
			Map<String, String> NOTCHtoSRG = new HashMap<>();
			Map<String, String> SRGtoMCP = new HashMap<>();

			in.useDelimiter(",");
			while(in.hasNextLine()) {
				String SRGname = stripQuotes(in.next());
				String MCPname = stripQuotes(in.next());
				String NOTCHname = stripQuotes(in.next());
				in.next(); //Skip sig
				in.next(); //Skip notchsig
				String clazz = stripQuotes(in.next());
				String obfClazz = stripQuotes(in.next());
				String pack = stripQuotes(in.next());
				String sideNumber = in.nextLine().substring(2, 3); //Remove the last comma and surrounding quotes

				try {
					if (Integer.parseInt(sideNumber) == side) {
						if (reverse)
							NOTCHtoSRG.put(pack + '/' + clazz + '/' + SRGname, NOTCHname);
						else
							NOTCHtoSRG.put(obfClazz + '/' + NOTCHname, SRGname);
						SRGtoMCP.put(SRGname, MCPname);
					}
				} catch(NumberFormatException e) {
					//This fails on the header line
				}
			}
			rv.fields = NOTCHtoSRG;
			rv.SRGtoMCPfields = SRGtoMCP;
		}

		return rv;
	}

	@Deprecated
	public SrgFile(File f, boolean reverse) throws IOException {
		if (f.exists()) {//Older formats had a classes.csv instead of a srg file
			try (FileReader fr = new FileReader(f)) {
				SrgFile sf = read(new BufferedReader(fr), reverse);
				classes = sf.classes;
				fields = sf.fields;
				methods = sf.methods;
			}
		} else {
			int side;
			switch (f.getName()) {
			case "client.srg":
				side = 0;
				break;
				
			case "server.srg":
				side = 1;
				break;
				
			default:
				throw new AssertionError("Unexpected srg file name: "+f.getName());
			}

			SrgFile sf = readOld(f.getParentFile(), side, reverse);

			classes = sf.classes;
			fields = sf.fields;
			methods = sf.methods;
			SRGtoMCPfields = sf.SRGtoMCPfields;
			SRGtoMCPmethods = sf.SRGtoMCPmethods;
			oldStyle = true;
		}
	}
	
	public Mapping toMapping(NameSet fromNS, NameSet toNS) {
		Mapping m = new Mapping(fromNS, toNS);
		
		for(Map.Entry<String, String> entry : classes.entrySet()) {
			m.setClass(entry.getKey(), entry.getValue());
		}
		
		for(Map.Entry<String, String> entry : fields.entrySet()) {
			int i = entry.getKey().lastIndexOf('/');
			m.setField(entry.getKey().substring(0, i), entry.getKey().substring(i+1), entry.getValue());
		}
		
		for(Map.Entry<String, String> entry : methods.entrySet()) {
			int i = entry.getKey().lastIndexOf('(');
			String desc = entry.getKey().substring(i);
			String classandname = entry.getKey().substring(0,i);
			i = classandname.lastIndexOf('/');
			m.setMethod(classandname.substring(0,i), classandname.substring(i+1), desc, entry.getValue());
		}
		
		return m;
	}
}
