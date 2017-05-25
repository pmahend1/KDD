package randomForest.group3;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

public class AssociationActionRules {
	public static class JobMapper extends
			Mapper<LongWritable, Text, Text, Text> {

		boolean falseParameters;
		int lineCount = 0;

		double minSupport, minConfidence;

		String decisionAttribute, decisionFromAttribute, decisionToAttribute;

		public static ArrayList<String> attributeNamesList, stableAttributesList,
				stableAttributeValuesList, decisionFromAndToList;
		ArrayList<HashSet<String>> associations;

		static Map<ArrayList<String>, Integer> dataMap;
		static Map<String, HashSet<String>> distinctAttributeMap,
				decisionValuesMap;
		static Map<HashSet<String>, HashSet<String>> attributeValues;
		Map<ArrayList<ArrayList<String>>, ArrayList<String>> actionMap,
				derivedActionMap;
		Map<String, ArrayList<ArrayList<String>>> singleSet;

		public JobMapper() {
			super();

			attributeNamesList = new ArrayList<String>();
			stableAttributesList = new ArrayList<String>();
			stableAttributeValuesList = new ArrayList<String>();
			decisionFromAndToList = new ArrayList<String>();
			associations = new ArrayList<HashSet<String>>();

			dataMap = new HashMap<ArrayList<String>, Integer>();
			distinctAttributeMap = new HashMap<String, HashSet<String>>();
			decisionValuesMap = new HashMap<String, HashSet<String>>();
			attributeValues = new HashMap<HashSet<String>, HashSet<String>>();
			actionMap = new HashMap<ArrayList<ArrayList<String>>, ArrayList<String>>();
			derivedActionMap = new HashMap<ArrayList<ArrayList<String>>, ArrayList<String>>();
			singleSet = new HashMap<String, ArrayList<ArrayList<String>>>();
		}

		@Override
		protected void setup(
				Mapper<LongWritable, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {

			attributeNamesList = new ArrayList<String>(Arrays.asList(context
					.getConfiguration().getStrings("attributes")));
			stableAttributesList = new ArrayList<String>(Arrays.asList(context
					.getConfiguration().getStrings("stable")));
			decisionAttribute = context.getConfiguration().getStrings(
					"decision")[0];
			decisionFromAttribute = context.getConfiguration().get("decisionFrom");
			decisionToAttribute = context.getConfiguration().get("decisionTo");
			decisionFromAndToList.add(decisionFromAttribute);
			decisionFromAndToList.add(decisionToAttribute);
			minSupport = Double.parseDouble(context.getConfiguration().get(
					"support"));
			minConfidence = Double.parseDouble(context.getConfiguration().get(
					"confidence"));

			super.setup(context);
		}

		@Override
		protected void map(LongWritable key, Text inputValue,
				Mapper<LongWritable, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {

			splitData(inputValue, lineCount);

			// Getting stable attribute values
			for (int i = 0; i < stableAttributesList.size(); i++) {
				HashSet<String> distinctStableValues = distinctAttributeMap
						.get(stableAttributesList.get(i));

				for (String string : distinctStableValues) {
					if (!stableAttributeValuesList.contains(string))
						stableAttributeValuesList.add(string);
					else
						continue;
				}

			}

			setDecisionAttributeValues();

			lineCount++;
		}

		// Splitting the input data
		private void splitData(Text inputValue, int lineCount) {
			int lineNo = lineCount;

			String inputData = inputValue.toString();
			ArrayList<String> lineData = new ArrayList<String>(
					Arrays.asList(inputData.split("\t|,")));

			if (!checkEmptyValueInStringArray(lineData)) {
				String key;

				lineNo++;

				ArrayList<String> tempList = new ArrayList<String>();
				HashSet<String> set;

				for (int j = 0; j < lineData.size(); j++) {

					String currentAttributeValue = lineData.get(j);
					String attributeName = attributeNamesList.get(j);
					key = attributeName + currentAttributeValue;

					tempList.add(key);


					if (distinctAttributeMap.containsKey(attributeName)) {
						set = distinctAttributeMap.get(attributeName);

					} else {
						set = new HashSet<String>();
					}

					set.add(key);
					// Setting attribute values to the corresponding attribute
					distinctAttributeMap.put(attributeName, set);
				}

				if (!dataMap.containsKey(tempList)) {
					dataMap.put(tempList, 1);

					for (String listKey : tempList) {
						HashSet<String> mapKey = new HashSet<String>();
						mapKey.add(listKey);
						setMap(attributeValues, mapKey, lineNo);
					}
				} else
					dataMap.put(tempList, dataMap.get(tempList) + 1);

			}

		}


		private static boolean checkEmptyValueInStringArray(
				ArrayList<String> lineData) {
			return lineData.contains("");
		}


		private static void setMap(
				Map<HashSet<String>, HashSet<String>> values,
				HashSet<String> key, int lineNo) {
			HashSet<String> tempSet = new HashSet<String>();

			if (values.containsKey(key)) {
				tempSet.addAll(values.get(key));
			}

			tempSet.add("x" + lineNo);
			values.put(key, tempSet);
		}


		private void setDecisionAttributeValues() {
			HashSet<String> distinctDecisionValues = distinctAttributeMap
					.get(decisionAttribute);
			for (String value : distinctDecisionValues) {
				HashSet<String> newHash = new HashSet<String>();
				HashSet<String> finalHash = new HashSet<String>();
				newHash.add(value);

				if (decisionValuesMap.containsKey(value)) {
					finalHash.addAll(decisionValuesMap.get(value));
				}
				if (attributeValues.containsKey(newHash))
					finalHash.addAll(attributeValues.get(newHash));

				decisionValuesMap.put(value, finalHash);

			}
		}

		@Override
		protected void cleanup(
				Mapper<LongWritable, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {
			fillAttributeValues();

			while (!actionMap.isEmpty()) {
				combineFrequentActions(context);
			}

			super.cleanup(context);
		}

		private void fillAttributeValues() {
			ArrayList<String> processedStableAttributes = new ArrayList<String>();

			for (Map.Entry<HashSet<String>, HashSet<String>> set : attributeValues
					.entrySet()) {
				int support = set.getValue().size();
				if (support >= minSupport) {
					ArrayList<ArrayList<String>> outerSet = new ArrayList<ArrayList<String>>();
					ArrayList<String> innerSet = new ArrayList<String>();
					ArrayList<String> attributeNames = new ArrayList<String>();

					String primeAttribute = null;

					for (String element : set.getKey()) {
						innerSet.add(element);
						innerSet.add(element);
						outerSet.add(innerSet);

						primeAttribute = getAttributeName(element);
						attributeNames.add(primeAttribute);
						attributeNames.add(String.valueOf(support));
					}

					if (primeAttribute != null) {
						actionMap.put(outerSet, attributeNames);

						ArrayList<ArrayList<String>> forValue = new ArrayList<ArrayList<String>>();
						if (singleSet.get(primeAttribute) != null) {
							forValue = singleSet.get(primeAttribute);
						}
						forValue.add(innerSet);

						singleSet.put(primeAttribute, forValue);


						if (!isStable(primeAttribute)
								&& !processedStableAttributes
										.contains(primeAttribute)) {
							ArrayList<String> distinctAttributeValuesAAR = new ArrayList<String>();

							distinctAttributeValuesAAR
									.addAll(distinctAttributeMap
											.get(primeAttribute));


							for (int i = 0; i < distinctAttributeValuesAAR
									.size(); i++) {
								for (int j = 0; j < distinctAttributeValuesAAR
										.size(); j++) {

									if (i != j) {
										HashSet<String> left = new HashSet<String>(
												Arrays.asList(distinctAttributeValuesAAR
														.get(i)));
										HashSet<String> right = new HashSet<String>(
												Arrays.asList(distinctAttributeValuesAAR
														.get(j)));

										support = Math.min(
												attributeValues.get(left)
														.size(),
												attributeValues.get(right)
														.size());

										if (support >= minSupport) {
											processedStableAttributes
													.add(primeAttribute);

											outerSet = new ArrayList<ArrayList<String>>();
											innerSet = new ArrayList<String>();

											innerSet.addAll(left);
											innerSet.addAll(right);

											outerSet.add(innerSet);
											attributeNames.set(1,
													String.valueOf(support));

											actionMap.put(outerSet,
													attributeNames);

											forValue = singleSet
													.get(primeAttribute);
											forValue.add(innerSet);

											singleSet.put(primeAttribute,
													forValue);

										}
									}
								}
							}
						}
					}
				}
			}
		}

		public static String getAttributeName(String value1) {
			for (Map.Entry<String, HashSet<String>> entryValue : distinctAttributeMap
					.entrySet()) {
				if (entryValue.getValue().contains(value1)) {
					return entryValue.getKey();
				}
			}
			return null;
		}

		public boolean isStable(String value) {
			if (stableAttributeValuesList.containsAll(distinctAttributeMap
					.get(value)))
				return true;
			else
				return false;
		}

		private void combineFrequentActions(
				Mapper<LongWritable, Text, Text, Text>.Context context) {

			derivedActionMap = new HashMap<ArrayList<ArrayList<String>>, ArrayList<String>>();
			for (Map.Entry<ArrayList<ArrayList<String>>, ArrayList<String>> mainSet : actionMap
					.entrySet()) {
				ArrayList<ArrayList<String>> mainKey = mainSet.getKey();
				ArrayList<String> mainValue = mainSet.getValue();

				for (Map.Entry<String, ArrayList<ArrayList<String>>> attrSet : singleSet
						.entrySet()) {
					String attrKey = attrSet.getKey();

					if (((mainValue.contains(decisionAttribute) && mainKey
							.contains(decisionFromAndToList)) || (attrKey
							.equals(decisionAttribute) && attrSet.getValue()
							.contains(decisionFromAndToList)))
							&& !mainValue.contains(attrKey)) {

						for (ArrayList<String> extraSet : attrSet.getValue()) {
							ArrayList<String> toCheckIn = new ArrayList<String>();
							ArrayList<String> toCheckOut = new ArrayList<String>();
							ArrayList<String> newValue = new ArrayList<String>();
							ArrayList<ArrayList<String>> newKey = new ArrayList<ArrayList<String>>();

							newKey.addAll(mainKey);
							newKey.add(extraSet);

							newValue.addAll(mainValue.subList(0,
									mainValue.size() - 1));
							newValue.add(attrKey);

							// To handle duplicates
							HashSet<String> newAssociation = new HashSet<String>();
							for (ArrayList<String> checkMultipleValues : newKey) {
								String toAddIntoAssociations = "";

								String left = checkMultipleValues.get(0);
								String right = checkMultipleValues.get(1);

								toCheckIn.add(left);
								toAddIntoAssociations += left + " -> ";

								toCheckOut.add(right);
								toAddIntoAssociations += right;

								newAssociation.add(toAddIntoAssociations);
							}

							if (!associations.contains(newAssociation)) {

								int support = Math.min(
										findLERSSupport(toCheckIn),
										findLERSSupport(toCheckOut));
								newValue.add(String.valueOf(support));

								if (support >= minSupport) {
									derivedActionMap.put(newKey, newValue);
									printFrequentActions(newKey, newValue,
											context);
									associations.add(newAssociation);
								}

							}
						}
					}
				}
			}

			actionMap.clear();
			actionMap.putAll(derivedActionMap);
			derivedActionMap.clear();
		}

		private static int findLERSSupport(ArrayList<String> tempList) {
			int count = 0;

			for (Map.Entry<ArrayList<String>, Integer> entry : dataMap.entrySet()) {
				if (entry.getKey().containsAll(tempList)) {
					count += entry.getValue();
				}
			}

			return count;
		}

		private void printFrequentActions(
				final ArrayList<ArrayList<String>> key,
				final ArrayList<String> value,
				final Mapper<LongWritable, Text, Text, Text>.Context context) {

			if (value.contains(decisionAttribute)) {

				String rule = "", decision = "", decisionFrom = "", decisionTo = "";
				int count = 0;
				ArrayList<String> actionFrom = new ArrayList<String>();
				ArrayList<String> actionTo = new ArrayList<String>();

				for (ArrayList<String> list : key) {
					if (value.get(count).equals(decisionAttribute)) {
						decisionFrom = list.get(0);
						decisionTo = list.get(1);
						decision = "(" + value.get(count) + "," + decisionFrom
								+ " ->  " + decisionTo + ")";
					} else {
						if (!rule.equals("")) {
							rule += "^";
						}

						rule += "(" + value.get(count) + "," + list.get(0)
								+ " ->  " + list.get(1) + ")";

						actionFrom.add(list.get(0));
						actionTo.add(list.get(1));
					}

					count++;
				}

				if (!rule.equals("")
						&& !stableAttributeValuesList.containsAll(actionFrom)) {
					rule += " ==> " + decision;

					final String finalRule = rule;
					final String finalDecisionFrom = decisionFrom;
					final String finalDecisionTo = decisionTo;

					String suppConf = calculateAssociationActionRuleSupport(actionFrom,
							actionTo, finalDecisionFrom, finalDecisionTo,
							minSupport, minConfidence);

					if (!suppConf.equals("")) {
						try {
							Text key1 = new Text(finalRule);
							Text value1 = new Text(suppConf);
							if (key1.toString().indexOf(decisionAttribute) < key1
									.toString().indexOf(decisionFromAndToList.get(0)))
								if (key1.toString().indexOf(
										decisionFromAndToList.get(0)) < key1
										.toString().indexOf(
												decisionFromAndToList.get(1)))
									context.write(key1, value1);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				}
			}

		}

		public String calculateAssociationActionRuleSupport(ArrayList<String> actionFrom,
				ArrayList<String> actionTo, String decisionFrom,
				String decisionTo, double minSupp, double minConf) {
			String supportConfidence = new String();

			ArrayList<String> left = new ArrayList<String>();
			ArrayList<String> right = new ArrayList<String>();

			left.addAll(actionFrom);
			left.add(decisionFrom);
			right.addAll(actionTo);
			right.add(decisionTo);

			double leftRuleSupport = findLERSSupport(left);
			double rightRuleSupport = findLERSSupport(right);
			double leftSupport = findLERSSupport(actionFrom);
			double rightSupport = findLERSSupport(actionTo);

			double support = Math.min(leftRuleSupport, rightRuleSupport);
			double confidence = (leftRuleSupport / leftSupport)
					* (rightRuleSupport / rightSupport) * 100;

			if (confidence >= minConf && support >= minSupp)
				supportConfidence = support + "," + confidence;

			return supportConfidence;
		}

	}

	public static class JobReducer extends Reducer<Text, Text, Text, Text> {

		@Override
		protected void reduce(Text key, Iterable<Text> values,
				Reducer<Text, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {

			int mapper = Integer.parseInt(context.getConfiguration().get(
					"mapred.map.tasks"));
			int min_presence = mapper / 2;
			int sum = 0;
			double support = 0, confidence = 0;
			DecimalFormat df = new DecimalFormat("###.##");
			for (Text val : values) {
				sum++;
				support = support
						+ Double.valueOf(df.format(Double.parseDouble(val
								.toString().split(",")[0])));
				confidence = confidence
						+ Double.valueOf(df.format(Double.parseDouble(val
								.toString().split(",")[1])));
			}
			if (sum >= min_presence) {
				context.write(
						key,
						new Text("[Support: "
								+ Double.valueOf(df.format(support / sum))
								+ ", Confidence: "
								+ Double.valueOf(df.format(confidence / sum))
								+ "%]"));
			}
		}
	}
}