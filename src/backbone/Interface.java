package backbone;

import interactor.Interactor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import ser2mat.ser2mat;
import cell.CModel;

public class Interface{

	public static void main(String[] args) throws Exception{
		System.out.println("DIATOMAS Java model");

		// Initialise model, simulation and create an object for a copy
		CModel model = new CModel();
		Run instance;
		// Analyse command line arguments, immediately execute some, save rest to Hashtable
		int NArg = args.length;
		Map<String, String> argument = Collections.synchronizedMap(new LinkedHashMap<String, String>());
		for(int ii=0; ii<NArg; ii+=2) {
			String arg = args[ii];
			// Case help file
			if(arg.equalsIgnoreCase("help") || arg.equalsIgnoreCase("--help") || arg.equalsIgnoreCase("?") || arg.equalsIgnoreCase("/?")) {
				System.out.println("Usage: java -jar diatomas.jar [option0] [value0] [option1] [value1] ...");
				System.out.println("where [value] be a number (and 0 == false, 1 == true) or string");
				System.out.println("");
				System.out.println("args\t\t\t\t Shows all possible model input arguments");
				System.out.println("load [path/filename.seg]\t Load the specified file instead of the default parameters (do not include the results/ folder). Automatically starts model after loading");
				System.out.println("ser2mat [path] \t\t\t Converts all .ser files found in [path]/output/ to .mat files. Automatically inhibits model starting after loading");
				return;
			// Case argument enumeration
			} else if(arg.equalsIgnoreCase("args")) {
				System.out.println("Possible command line arguments:");
				int counter = 1;
				for(Field field : CModel.class.getFields()) {
					System.out.print(String.format("%-25s",field.getName()));
					if(counter%3==0)	System.out.println("");		
					counter++;
				}
				return;
			// Case convert serialised files to MATLAB files
			} else if(arg.equalsIgnoreCase("ser2mat")) {
				// Convert ser to mat files
				String modelPath = args[ii+1];
				// Open directory
				File dir = new File(modelPath + "/output/");
				// Construct filter
				FilenameFilter filter = new FilenameFilter() {
				    public boolean accept(File dir, String name) {
				    	return name.endsWith(".ser");
				    }
				};
				// List filtered files and convert
				String[] files = dir.list(filter);
				if(files==null) throw new Exception("No .ser files found in directory " + modelPath + "/output/");
				java.util.Arrays.sort(files);
				for(String fileName : files) { 
					model.Write("Loading " + fileName,"", true, false);
					String loadPath = modelPath + "/output/" + fileName;
					model = Load(loadPath);
					ser2mat.Convert(model);
				}
				return;
			// Case argument needs to be set in the model
			} else {
				// Save all other arguments in the hashtable
				argument.put(arg.toLowerCase(), args[ii+1]);
			}
		}
		
		//
		
		if(argument.containsKey("load")){						// Iterations > 0
			String loadName = argument.get("load");
			if(!loadName.contains("/")) {
				// loadPath doesn't state which simulation to load --> load the most recent one
				// Open directory
				File dir = new File("results/" + loadName + "/output/");
				// Construct filter
				FilenameFilter filter = new FilenameFilter() {
					public boolean accept(File dir, String name) {
						return name.endsWith(".ser");
					}
				};
				// List filtered files
				String[] files = dir.list(filter);
				if(files==null) throw new Exception("No .ser files found in directory " + loadName + "/output/");
				// Update loadPath based on found .ser files
				java.util.Arrays.sort(files);
				loadName = "results/" + loadName + "/output/" + files[files.length-1];
			}
			model = Load(loadName);
			instance = new Run(model);
			model.Write("Loaded " + loadName, "");
			SetArgument(model, argument, true);
			
		} else {												// Start from zero
			// Set name to prevent writing things to default folder
			if(argument.containsKey("name"))		model.name = argument.get("name");
			// Initialise parameters
			instance = new Run(model);
			SetArgument(model, argument, false);				// Don't want to do multiplications (e.g. Kan[0] *0.1) just yet to prevent from doubling it up
			instance.Initialise();
			SetArgument(model, argument, true);
		}
		// Copy simulation .jar file to this folder
		model.Write("Copying .jar file to simulation folder", "");
		CopyJar(model);
		// Done analysing input arguments and preparing. Start model
		try {
			model.Write("=====================================", "");
			String message = "Starting simulation '" + model.name + "' w/ arguments: ";
			for(int jj=0; jj<args.length; jj++) 	message += args[jj] + " ";
			model.Write(message,"");
			model.Write("=====================================", "");

			// Commence the simulation
			instance.Start();
		} catch (RuntimeException E) {
			StringWriter sw = new StringWriter();				// We need this line and below to get the exception as a string for the log
			PrintWriter pw = new PrintWriter(sw);
			E.printStackTrace(pw);								// Write the exception stack trace to a print
			model.Write(sw.toString(), "error", false, true);	// Convert stack trace to string, print to log
			E.printStackTrace();								// Throw the error so the simulation stops running
		}
	}
			
	public static void SetArgument(CModel model, Map<String, String> argument, boolean doRelative) {
		Iterator<Entry<String, String>> argumentKeys = argument.entrySet().iterator();
		args:while(argumentKeys.hasNext()) {
			Entry<String, String> iter = argumentKeys.next(); 
			String keyRaw = iter.getKey();
			String key = keyRaw.contains("[") ? keyRaw.split("\\[")[0] : keyRaw;	// Double escape was necessary. Remove the part at and after "[" if present
			String value = iter.getValue();											
			for(Field field : CModel.class.getFields()) {
				if(key.equalsIgnoreCase(field.getName())) {
					key = field.getName();						// Update key to the correct Capitalisation
					try {
						@SuppressWarnings("rawtypes")
						Class fieldClass = CModel.class.getField(key).get(model).getClass();
						// If the field is any kind of array
						if(field.get(model).getClass().isArray()) {
							// We change only a single index
							if(keyRaw.contains("[")) {
								String[] keySplit = keyRaw.split("\\[");
								String iiString = keySplit[1];
								iiString = iiString.replace("[","");
								iiString = iiString.replace("]","");
								// Array of array (matrix)
								if(keySplit.length>2) {
									String fieldClassName = fieldClass.getComponentType().getComponentType().getName();
									String jjString = keySplit[2];
									jjString = jjString.replace("[","");
									jjString = jjString.replace("]","");
									int ii = Integer.parseInt(iiString);
									int jj = Integer.parseInt(jjString);
									// boolean[][]					// OPTIMISE: We could make this with generics, virtually the same code is used for all these types
									if(fieldClassName.equals("boolean")) {
										boolean[][] bool = (boolean[][]) field.get(model);
										if(bool[ii][jj] = (Integer.parseInt(value) == 1 ? true : false)) {
											bool[ii][jj] = Integer.parseInt(value) == 1 ? true : false;
											field.set(model, bool);
											model.Write(field.getName() + "[" + ii + "][" + jj + "] set to " + (bool[ii][jj]?"true":"false"), "");
										}
										continue args;
									}
									// double[][]
									if(fieldClassName.equals("double")) {
//										double[][] numberRef = (double[][]) field.get(modelRef);
										double[][] number = (double[][]) field.get(model);
										// See if we have a relative (e.g. Ka[0] *10) or absolute (e.g. Ka[0] 1e-10) value
										if(value.startsWith("*")) {	// Relative
											double multiplier = Double.parseDouble(value.substring(1));		// Cut off *
											if(multiplier != 1.0 && doRelative) {
												number[ii][jj] *= multiplier;
												field.set(model, number);
												model.Write(field.getName() + "[" + ii + "][" + jj + "] set to " + number[ii][jj], "");
											}
										} else {
											if(number[ii][jj] != Double.parseDouble(value)) {
												number[ii][jj] = Double.parseDouble(value);
												field.set(model, number);
												model.Write(field.getName() + "[" + ii + "][" + jj + "] set to " + number[ii][jj], "");
											}
										}
										continue args;
									}
									// int[][]
									if(fieldClassName.equals("int")) {
										int[][] number = (int[][]) field.get(model);
										if(number[ii][jj] != Integer.parseInt(value)) {
											number[ii][jj] = Integer.parseInt(value);
											field.set(model, number);
											model.Write(field.getName() + "[" + ii + "][" + jj + "] set to " + number[ii][jj], "");
										}
										continue args;
									}
									// String[][]
									if(fieldClassName.equals("String")) {
										String[][] string = (String[][]) field.get(model);
										if(value.equals(string[ii][jj])) {
											string[ii][jj] = value;
											field.set(model, string);
											model.Write(field.getName() + "[" + ii + "][" + jj + "] set to " + string[ii][jj], "");
										}
										continue args;
									}	
								// Array (vector)									
								} else {
									String fieldClassName = fieldClass.getComponentType().getName();
									int ii = Integer.parseInt(iiString);
									// boolean[]
									if(fieldClassName.equals("boolean")) {
										boolean[] bool = (boolean[]) field.get(model);
										if(bool[ii] != (Integer.parseInt(value) == 1 ? true : false)) {
											bool[ii] = (Integer.parseInt(value) == 1 ? true : false);
											model.Write(field.getName() + "[" + ii + "] set to " + (bool[ii]?"true":"false"), "");
											field.set(model, bool);
										}
										continue args;
									}
									// double[]
									if(fieldClassName.equals("double")) {
										double[] number = (double[]) field.get(model);
										// See if we have a relative (e.g. Ka[0] *10) or absolute (e.g. Ka[0] 1e-10) value
										if(value.startsWith("*")) {	// Relative
											double multiplier = Double.parseDouble(value.substring(1));		// Cut off *
											if(multiplier != 1.0 && doRelative) {
												number[ii] *= multiplier;
												field.set(model, number);
												model.Write(field.getName() + "[" + ii + "] set to " + number[ii], "");
											}
										} else {
											if(number[ii] != Double.parseDouble(value)) {
												number[ii] = Double.parseDouble(value);
												field.set(model, number);
												model.Write(field.getName() + "[" + ii + "] set to " + number[ii], "");
											}
										}
										continue args;
									}
									// int[]
									if(fieldClassName.equals("int")) {
										int[] number = (int[]) field.get(model);
										if(number[ii] != Integer.parseInt(value)) {
											number[ii] = Integer.parseInt(value);
											model.Write(field.getName() + "[" + ii + "] set to " + number[ii], "");
											field.set(model, number);
										}
										continue args;
									}
									// String[]
									if(fieldClassName.equals("String")) {
										String[] string = (String[]) field.get(model);
										if(string[ii] != value) {
											string[ii] = value;
											field.set(model, string);
											model.Write(field.getName() + "[" + ii + "] set to " + string[ii], "");
										}
										continue args;
									}	
								}
							// We make an entirely new array
							} else {
								String fieldClassName = fieldClass.getComponentType().getName();
								String[] splitValue = value.split(",");			// Split at comma
								for(int ii=0; ii<splitValue.length; ii++) {		// Replace all curly braces
									splitValue[ii] = splitValue[ii].replace("{","");
									splitValue[ii] = splitValue[ii].replace("}","");
								}
								// boolean[]
								if(fieldClassName.equals("boolean")) {
									boolean[] bool = new boolean[splitValue.length];
									for(int ii=0; ii<splitValue.length; ii++) {
										if(bool[ii] != (Integer.parseInt(splitValue[ii]) == 1 ? true : false)) {
											bool[ii] = Integer.parseInt(splitValue[ii]) == 1 ? true : false;
											model.Write(field.getName() + "[" + ii + "] set to " + (bool[ii]?"true":"false"), "");
											field.set(model, bool);
										}
									}
									continue args;
								}
								// double[]
								if(fieldClassName.equals("double")) {
									double[] number = new double[splitValue.length];
									double[] numberOld = (double[]) field.get(model);
									for(int ii=0; ii<splitValue.length; ii++) {
										// See if we have a relative (e.g. Ka[0] *10) or absolute (e.g. Ka[0] 1e-10) value
										if(splitValue[ii].startsWith("*")) {											// Relative
											double multiplier = Double.parseDouble(splitValue[ii].substring(1));		// Cut off *
											if(multiplier != 1.0 && doRelative) {
												number[ii] = numberOld[ii] * multiplier;
												field.set(model, number);
												model.Write(field.getName() + "[" + ii + "] set to " + number[ii], "");
											}
											
										} else {
											if(number[ii] != numberOld[ii]) {											// Here we already have numberOld, so we can use this
												number[ii] = Double.parseDouble(splitValue[ii]);
												field.set(model, number);
												model.Write(field.getName() + "[" + ii + "] set to " + number[ii], "");
											}
										}
										
									}
									continue args;
								}
								// int[]
								if(fieldClassName.equals("int")) {
									int[] number = new int[splitValue.length];
									for(int ii=0; ii<splitValue.length; ii++) {
										if(number[ii] != Integer.parseInt(splitValue[ii])) {
											number[ii] = Integer.parseInt(splitValue[ii]);
											field.set(model, number);
											model.Write(field.getName() + "[" + ii + "] set to " + number[ii], "");
										}
									}
									continue args;
								}
								// String[]
								if(fieldClassName.equals("String")) {
									String[] string = new String[splitValue.length];
									for(int ii=0; ii<splitValue.length; ii++) {
										if(splitValue[ii].equals(string[ii])) {
											string[ii] = splitValue[ii];
											field.set(model, string);
											model.Write(field.getName() + "[" + ii + "] set to " + string[ii], "");
										}
									}
									continue args;
								}
							}
						// The field is NOT an array
						} else {
							String fieldClassName = fieldClass.getSimpleName();
							// boolean
							if(fieldClassName.equals("Boolean")) {
								boolean bool = Integer.parseInt(value) == 1 ? true : false;
								if(field.getBoolean(model) != bool) {
									field.setBoolean(model, bool);
									model.Write(field.getName() + " set to " + (bool?"true":"false"), "");
								}
								continue args;
							}
							// double
							if(fieldClassName.equals("Double")) {
								double number = field.getDouble(model);
								// See if we have a relative (e.g. Kan *10) or absolute (e.g. Kan 1e-10) value
								if(value.startsWith("*")) {						// Relative
									double multiplier = Double.parseDouble(value.substring(1));		// Cut off *
									if(multiplier != 1.0 && doRelative) {
										number = field.getDouble(model) * multiplier;				// Hasn't been multiplied before, so do it
										field.setDouble(model, number);
										model.Write(field.getName() + " set to " + number, "");
									}
								} else {
									number = Double.parseDouble(value);			// Absolute
									if(field.getDouble(model) != number) {
										field.setDouble(model, number);
										model.Write(field.getName() + " set to " + number, "");
									}
								}
								continue args;									// Check next argument (i.e. continue outer loop)
							}
							// int
							if(fieldClassName.equals("Integer")) {
								int number = Integer.parseInt(value);
								if(field.getInt(model) != number) {
									field.setInt(model, number);
									model.Write(field.getName() + " set to " + number, "");
								}
								continue args;
							}
							// String
							if(fieldClassName.equals("String")) {
								if(!value.equalsIgnoreCase((String) field.get(model))) {
									field.set(model, value);
									model.Write(field.getName() + " set to " + value, "");
								}
								continue args;
							}
							// Throw an error
							throw new RuntimeException("Unknown class type");
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
			// Are you still here?
			if(!key.equalsIgnoreCase("load"))		throw new RuntimeException("Unknown argument: " + key);
		}
	}

	public static CModel Load(String loadPath) {
		FileInputStream fis = null;
		GZIPInputStream gz = null;
		ObjectInputStream ois = null;
		CModel model = null;
		
		try {
			fis = new FileInputStream(loadPath);
			gz = new GZIPInputStream(fis);
			ois = new ObjectInputStream(gz);
			model = (CModel) ois.readObject();
			ois.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
		
		// Update model name
		String[] splitLoadPath = loadPath.split("/");
		model.name = splitLoadPath[1];					// Used to be 0, but now 1 because the first will be results
		
		return model;
	}
	
	public static void CopyJar(CModel model) {
		// Construct date and time
		DateFormat dateFormat = new SimpleDateFormat("yyMMdd_HHmmss");
		Calendar cal = Calendar.getInstance();
		// Extract format from input arguments
		String dateTime = dateFormat.format(cal.getTime());
		// Copy file, so determine if this is Windows (copy) or Linux/Mac (cp)
		String response = null;
		if(Interactor.getOS().compareTo("Windows")==0) {
			response = Interactor.executeCommand("copy ./diatomas.jar ./results/" + model.name + "/diatomas_" + dateTime + ".jar", true, false);
		} else {
			response = Interactor.executeCommand("cp ./diatomas.jar ./results/" + model.name + "/diatomas_" + dateTime + ".jar", true, false);
		}
		if(!(response.compareTo("")==0)) {
			model.Write("Couldn't copy model .jar file: \n" + response, "warning");
		}
		return;
	}
}