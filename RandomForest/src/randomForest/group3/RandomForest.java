package randomForest.group3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class RandomForest {

	public static ArrayList<String> attributeArrayList = new ArrayList<String>();
	public static ArrayList<String> dataFileList = new ArrayList<String>();
	public static ArrayList<String> originalAttributes = new ArrayList<String>();
	public static ArrayList<String> stableAttributes = new ArrayList<String>();
	public static ArrayList<String> decisionAttributes = new ArrayList<String>();
	public static ArrayList<String> decisionFromAndTo = new ArrayList<String>();
	public static String decisionFrom = new String();
	public static String decisionTo = new String();
	public static int minSupport = 0;
	public static int minConfidence = 0;
	public static Scanner inputScanner = new Scanner(System.in);
	public static int index = 0;


	public static void main(final String[] args) throws IOException,
			ClassNotFoundException, InterruptedException {

		File attribute = new File(args[0]);
		FileReader attribute_reader = new FileReader(attribute);
		BufferedReader attribute_buffer = new BufferedReader(attribute_reader);
		String att = new String();
		while ((att = attribute_buffer.readLine()) != null) {
			attributeArrayList.addAll(Arrays.asList(att.split("\\s+")));
			originalAttributes.addAll(Arrays.asList(att.split("\\s+")));
		}
		int count = 0;
		attribute_reader.close();
		attribute_buffer.close();
		File data = new File(args[1]);
		FileReader data_reader = new FileReader(data);
		BufferedReader data_buffer = new BufferedReader(data_reader);
		String d = new String();
		while ((d = data_buffer.readLine()) != null) {
			count++;
		}
		data_reader.close();
		data_buffer.close();
		setStableAttributes();
		setDecisionAttribute();
		setDecisionFromAndTo(args[1]);
		System.out.println("Please enter minimum Support: ");
		minSupport = inputScanner.nextInt();
		System.out.println("Please enter minimum Confidence %: ");
		minConfidence = inputScanner.nextInt();
		inputScanner.close();

		Configuration configuration = new Configuration();

		configuration.set("mapred.max.split.size", data.length() / 5 + "");
		configuration.set("mapred.min.split.size", "0");

		configuration.setInt("count", count);
		configuration.setStrings(
				"attributes",
				Arrays.copyOf(originalAttributes.toArray(),
						originalAttributes.toArray().length, String[].class));
		configuration.setStrings(
				"stable",
				Arrays.copyOf(stableAttributes.toArray(),
						stableAttributes.toArray().length, String[].class));
		configuration.setStrings(
				"decision",
				Arrays.copyOf(decisionAttributes.toArray(),
						decisionAttributes.toArray().length, String[].class));
		configuration.setStrings("decisionFrom", decisionFrom);
		configuration.setStrings("decisionTo", decisionTo);
		configuration.setStrings("support", minSupport + "");
		configuration.setStrings("confidence", minConfidence + "");

		Job actionRulesJob;
		actionRulesJob = Job.getInstance(configuration);

		actionRulesJob.setJarByClass(ActionRules.class);

		actionRulesJob.setMapperClass(ActionRules.JobMapper.class);
		actionRulesJob.setReducerClass(ActionRules.JobReducer.class);

		actionRulesJob.setNumReduceTasks(1);

		actionRulesJob.setOutputKeyClass(Text.class);
		actionRulesJob.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(actionRulesJob, new Path(args[1]));

		FileOutputFormat.setOutputPath(actionRulesJob, new Path(args[2]));

		actionRulesJob.waitForCompletion(true);

		Job associationActionRulesJob;
		associationActionRulesJob = Job.getInstance(configuration);

		associationActionRulesJob.setJarByClass(AssociationActionRules.class);

		associationActionRulesJob
				.setMapperClass(AssociationActionRules.JobMapper.class);
		associationActionRulesJob
				.setReducerClass(AssociationActionRules.JobReducer.class);

		associationActionRulesJob.setNumReduceTasks(1);

		associationActionRulesJob.setOutputKeyClass(Text.class);
		associationActionRulesJob.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(associationActionRulesJob, new Path(
				args[1]));

		FileOutputFormat.setOutputPath(associationActionRulesJob, new Path(
				args[3]));

		associationActionRulesJob.waitForCompletion(true);
	}
	

	public static void setStableAttributes() {
		boolean flag = false;
		String[] stable = null;
		System.out.println("-------------Random Forest Algorithm------------");
		System.out.println("Available attributes are: "
				+ attributeArrayList.toString());
		System.out.println("Please enter the Stable Attribute(s):");
		String s = inputScanner.next();
		if (s.split(",").length > 1) {
			stable = s.split(",");
			for (int j = 0; j < stable.length; j++) {
				if (!(attributeArrayList.contains(stable[j]))) {
					System.out.println("Invalid Stable attribute(s)");
					flag = true;
					break;
				}
			}
			if (flag == false) {
				stableAttributes.addAll(Arrays.asList(stable));
				attributeArrayList.removeAll(stableAttributes);
			}
		} else {
			if (!(attributeArrayList.contains(s))) {
				System.out.println("Invalid Stable attribute(s)");
			} else {
				stableAttributes.add(s);
				attributeArrayList.removeAll(stableAttributes);
			}
		}
		System.out.println("Stable Attribute(s): "
				+ stableAttributes.toString());
		System.out.println("Available Attribute(s): "
				+ attributeArrayList.toString());
	}

	public static void setDecisionAttribute() {
		System.out.println("1. Enter the Decision Attribute:");
		String s = inputScanner.next();
		if (!(attributeArrayList.contains(s))) {
			System.out.println("Invalid Decision attribute(s)");
		} else {
			decisionAttributes.add(s);
			index = originalAttributes.indexOf(s);
			attributeArrayList.removeAll(decisionAttributes);
		}
	}

	public static void setDecisionFromAndTo(String args) {
		HashSet<String> set = new HashSet<String>();
		File data = new File(args);
		FileReader fileReader;
		BufferedReader data_buffer;
		try {
			fileReader = new FileReader(data);
			data_buffer = new BufferedReader(fileReader);
			String str = new String();
			while ((str = data_buffer.readLine()) != null)
				set.add(str.split(",")[index]);
			fileReader.close();
			data_buffer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println();
		Iterator<String> iterator = set.iterator();
		while (iterator.hasNext()) {
			decisionFromAndTo.add(originalAttributes.get(index)
					+ iterator.next());
		}
		System.out.println("Available decision attributes are: "
				+ decisionFromAndTo.toString());
		System.out.println("Enter decision FROM attribute: ");
		decisionFrom = inputScanner.next();
		System.out.println("Enter decision TO Attribute: ");
		decisionTo = inputScanner.next();
		System.out.println("Stable attributes are: "
				+ stableAttributes.toString());
		System.out.println("Decision attribute is: "
				+ decisionAttributes.toString());
		System.out.println("Decision FROM : " + decisionFrom);
		System.out.println("Decision TO : " + decisionTo);
		System.out.println("Flexible Attribute(s) are: "
				+ attributeArrayList.toString());
	}
}
