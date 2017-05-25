package randomForest.group3;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

public class ActionRules {
	public static class JobMapper extends
			Mapper<LongWritable, Text, Text, Text> {

		int lineCount = 0;

		boolean falseParameters;

		double minSupport, minConfidence;

		String decisionAttribute, decisionFrom, decisionTo;

		public static ArrayList<String> attributeNamesList, stableAttributesList,
				stableAttributeValuesList;
		public static ArrayList<ArrayList<String>> actionRulesArrayList;

		static Map<ArrayList<String>, Integer> data;
		static Map<String, HashSet<String>> distinctAttributeValues,
				decisionValues;
		static Map<HashSet<String>, HashSet<String>> attributeValues,
				reducedAttributeValues;
		static Map<ArrayList<String>, HashSet<String>> markedValues,
				possibleRules;
		static Map<ArrayList<String>, String> certainRules;

		public JobMapper() {
			super();
			falseParameters = false;

			data = new HashMap<ArrayList<String>, Integer>();
			actionRulesArrayList = new ArrayList<ArrayList<String>>();
			attributeNamesList = new ArrayList<String>();
			stableAttributesList = new ArrayList<String>();
			stableAttributeValuesList = new ArrayList<String>();

			attributeValues = new HashMap<HashSet<String>, HashSet<String>>();
			reducedAttributeValues = new HashMap<HashSet<String>, HashSet<String>>();
			distinctAttributeValues = new HashMap<String, HashSet<String>>();
			decisionValues = new HashMap<String, HashSet<String>>();
			certainRules = new HashMap<ArrayList<String>, String>();
			markedValues = new HashMap<ArrayList<String>, HashSet<String>>();
			possibleRules = new HashMap<ArrayList<String>, HashSet<String>>();
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
			decisionFrom = context.getConfiguration().get("decisionFrom");
			decisionTo = context.getConfiguration().get("decisionTo");
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
				HashSet<String> distinctStableValues = distinctAttributeValues
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

					if (distinctAttributeValues.containsKey(attributeName)) {
						set = distinctAttributeValues.get(attributeName);

					} else {
						set = new HashSet<String>();
					}

					set.add(key);
					distinctAttributeValues.put(attributeName, set);
				}

				if (!data.containsKey(tempList)) {
					data.put(tempList, 1);

					for (String listKey : tempList) {
						HashSet<String> mapKey = new HashSet<String>();
						mapKey.add(listKey);
						setMap(attributeValues, mapKey, lineNo);
					}
				} else
					data.put(tempList, data.get(tempList) + 1);
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
			HashSet<String> distinctDecisionValues = distinctAttributeValues
					.get(decisionAttribute);
			for (String value : distinctDecisionValues) {
				HashSet<String> newHash = new HashSet<String>();
				HashSet<String> finalHash = new HashSet<String>();
				newHash.add(value);

				if (decisionValues.containsKey(value)) {
					finalHash.addAll(decisionValues.get(value));
				}
				if (attributeValues.containsKey(newHash))
					finalHash.addAll(attributeValues.get(newHash));

				decisionValues.put(value, finalHash);
				attributeValues.remove(newHash);
			}
		}


		@Override
		protected void cleanup(
				final Mapper<LongWritable, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {
			performLERS();

			generateActionRules(context);

			super.cleanup(context);
		}


		private void performLERS() {
			int loopCount = 0;

			while (!attributeValues.isEmpty()) {

				for (Map.Entry<HashSet<String>, HashSet<String>> set : attributeValues
						.entrySet()) {
					ArrayList<String> setKey = new ArrayList<String>();
					setKey.addAll(set.getKey());

					HashSet<String> setValue = set.getValue();

					if (set.getValue().isEmpty()) {
						continue;
					} else {
						for (Map.Entry<String, HashSet<String>> decisionSet : decisionValues
								.entrySet()) {

							if (decisionSet.getValue().containsAll(
									set.getValue())) {
								certainRules.put(setKey, decisionSet.getKey());
								markedValues.put(setKey, set.getValue());
								break;
							}
						}

					}

					if (!markedValues.containsKey(setKey)) {
						HashSet<String> possibleRulesSet = new HashSet<String>();
						for (Map.Entry<String, HashSet<String>> decisionSet : decisionValues
								.entrySet()) {

							possibleRulesSet.add(decisionSet.getKey());
						}

						if (possibleRulesSet.size() > 0)
							possibleRules.put(setKey, possibleRulesSet);
					}
				}

				removeMarkedValues();

				combinePossibleRules();
			}

		}


		static int calculateSupportLERS(ArrayList<String> key, String value) {
			ArrayList<String> tempList = new ArrayList<String>();

			for (String val : key) {
				if (!val.equals(""))
					tempList.add(val);
			}

			if (!value.equals(""))
				tempList.add(value);

			return findLERSSupport(tempList);

		}

		private static int findLERSSupport(ArrayList<String> tempList) {
			int count = 0;

			for (Map.Entry<ArrayList<String>, Integer> entry : data.entrySet()) {
				if (entry.getKey().containsAll(tempList)) {
					count += entry.getValue();
				}
			}

			return count;
		}


		static String calculateConfidenceLERS(ArrayList<String> key,
				String value) {
			int num = calculateSupportLERS(key, value);
			int den = calculateSupportLERS(key, "");
			int confidence = 0;

			if (den != 0) {
				confidence = (num * 100) / den;
			}

			return String.valueOf(confidence);

		}


		private static void removeMarkedValues() {
			for (Map.Entry<ArrayList<String>, HashSet<String>> markedSet : markedValues
					.entrySet()) {
				attributeValues.remove(new HashSet<String>(markedSet.getKey()));
			}

		}


		private static void combinePossibleRules() {
			Set<ArrayList<String>> keySet = possibleRules.keySet();
			ArrayList<ArrayList<String>> keyList = new ArrayList<ArrayList<String>>();
			keyList.addAll(keySet);

			for (int i = 0; i < possibleRules.size(); i++) {
				for (int j = (i + 1); j < possibleRules.size(); j++) {
					HashSet<String> combinedKeys = new HashSet<String>(
							keyList.get(i));
					combinedKeys.addAll(new HashSet<String>(keyList.get(j)));

					if (!checkSameGroup(combinedKeys)) {
						combineAttributeValues(combinedKeys);
					}
				}
			}

			removeRedundantValues();
			clearAttributeValues();
			possibleRules.clear();

		}


		public static boolean checkSameGroup(HashSet<String> combinedKeys) {
			ArrayList<String> combinedKeyAttributes = new ArrayList<String>();

			for (Map.Entry<String, HashSet<String>> singleAttribute : distinctAttributeValues
					.entrySet()) {
				for (String key : combinedKeys) {
					if (singleAttribute.getValue().contains(key)) {
						if (!combinedKeyAttributes.contains(singleAttribute
								.getKey()))
							combinedKeyAttributes.add(singleAttribute.getKey());
						else
							return true;
					}
				}

			}

			return false;
		}


		private static void combineAttributeValues(HashSet<String> combinedKeys) {
			HashSet<String> combinedValues = new HashSet<String>();

			for (Map.Entry<HashSet<String>, HashSet<String>> attributeValue : attributeValues
					.entrySet()) {
				if (combinedKeys.containsAll(attributeValue.getKey())) {
					if (combinedValues.isEmpty()) {
						combinedValues.addAll(attributeValue.getValue());
					} else {
						combinedValues.retainAll(attributeValue.getValue());
					}
				}
			}
			if (combinedValues.size() != 0)
				reducedAttributeValues.put(combinedKeys, combinedValues);
		}


		private static void removeRedundantValues() {
			HashSet<String> mark = new HashSet<String>();

			for (Map.Entry<HashSet<String>, HashSet<String>> reducedAttributeValue : reducedAttributeValues
					.entrySet()) {
				for (Map.Entry<HashSet<String>, HashSet<String>> attributeValue : attributeValues
						.entrySet()) {

					if (attributeValue.getValue().containsAll(
							reducedAttributeValue.getValue())
							|| reducedAttributeValue.getValue().isEmpty()) {
						mark.addAll(reducedAttributeValue.getKey());
					}
				}
			}

			reducedAttributeValues.remove(mark);

		}

		private static void clearAttributeValues() {
			attributeValues.clear();
			for (Map.Entry<HashSet<String>, HashSet<String>> reducedAttributeValue : reducedAttributeValues
					.entrySet()) {
				attributeValues.put(reducedAttributeValue.getKey(),
						reducedAttributeValue.getValue());
			}
			reducedAttributeValues.clear();
		}


		public ArrayList<String> generateActionRules(
				Mapper<LongWritable, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {
			ArrayList<String> actionFrom, actionTo, rules;
			ArrayList<String> actions = null;

			String rule = "";
			rules = new ArrayList<String>();

			for (Map.Entry<ArrayList<String>, String> certainRules1 : certainRules
					.entrySet()) {
				String certainRules1Value = certainRules1.getValue();

				if (certainRules1Value.equals(decisionFrom)) {

					for (Map.Entry<ArrayList<String>, String> certainRules2 : certainRules
							.entrySet()) {
						ArrayList<String> certainRules1Key = certainRules1
								.getKey();
						ArrayList<String> certainRules2Key = certainRules2
								.getKey();

						if ((certainRules1Key.equals(certainRules2Key))
								|| (!certainRules2.getValue()
										.equals(decisionTo))
								|| !checkStableAttributes(certainRules1Key,
										certainRules2Key)) {
							continue;
						} else {
							String primeAttribute = "";

							if (checkRulesSubSet(certainRules1Key,
									certainRules2Key)) {
														

								ArrayList<String> checkCertainValues1 = certainRules1
										.getKey();
								ArrayList<String> tempList = new ArrayList<String>();

								rule = "";
								actionFrom = new ArrayList<String>();
								actionTo = new ArrayList<String>();
								actions = new ArrayList<String>();

								for (String value1 : checkCertainValues1) {

									if (stableAttributeValuesList.contains(value1)) {
										if (!actionTo.contains(value1)) {
											rule = formRule(rule, value1,
													value1);

											actionFrom.add(value1);
											actionTo.add(value1);
											actions.add(getAction(value1,
													value1));
										}
										continue;
									} else {
										primeAttribute = getAttributeName(value1);

										ArrayList<String> checkCertainValues2 = certainRules2
												.getKey();
										for (String value2 : checkCertainValues2) {

											if (stableAttributeValuesList
													.contains(value2)) {
												if (!actionTo.contains(value2)) {
													rule = formRule(rule,
															value2, value2);

													actionFrom.add(value2);
													actionTo.add(value2);
													actions.add(getAction(
															value2, value2));
												}

											} else if (!(getAttributeName(value2)
													.equals(primeAttribute))) {
												tempList.add(value2);

											} else if (getAttributeName(value2)
													.equals(primeAttribute)
													&& !actionTo
															.contains(value2)) {
												rule = formRule(rule, value1,
														value2);

												actionFrom.add(value1);
												actionTo.add(value2);
												actions.add(getAction(value1,
														value2));
											}

										}
									}
								}

								for (String missedValues : tempList) {
									if (!actionTo.contains(missedValues)) {
										rule = formRule(rule, "", missedValues);

										actionFrom.add("");
										actionTo.add(missedValues);
										actions.add(getAction("", missedValues));
									}
								}

								printActionRule(actionFrom, actionTo, actions,
										rule, context);

								printExtraRules(actionFrom, actionTo, context);

							}
						}
					}
				}
			}

			return rules;
		}

		private static boolean checkStableAttributes(ArrayList<String> key,
				ArrayList<String> key2) throws IOException,
				InterruptedException {
			List<String> stableAttributesList1 = new ArrayList<String>();
			List<String> stableAttributesList2 = new ArrayList<String>();

			for (String value : key) {
				if (stableAttributeValuesList.contains(value))
					stableAttributesList1.add(value);
			}

			for (String value : key2) {
				if (stableAttributeValuesList.contains(value))
					stableAttributesList2.add(value);
			}

			if (stableAttributesList2.containsAll(stableAttributesList1))
				return true;
			else
				return false;
		}

		private boolean checkRulesSubSet(ArrayList<String> certainRules1,
				ArrayList<String> certainRules2) {
			ArrayList<String> primeAttributes1 = new ArrayList<String>();
			ArrayList<String> primeAttributes2 = new ArrayList<String>();

			for (String string : certainRules1) {
				String attributeName = getAttributeName(string);

				if (!isStable(attributeName))
					primeAttributes1.add(attributeName);
			}

			for (String string : certainRules2) {
				String attributeName = getAttributeName(string);

				if (!isStable(attributeName))
					primeAttributes2.add(attributeName);
			}

			if (primeAttributes2.containsAll(primeAttributes1))
				return true;
			else
				return false;
		}

		public static String getAttributeName(String value1) {
			for (Map.Entry<String, HashSet<String>> entryValue : distinctAttributeValues
					.entrySet()) {
				if (entryValue.getValue().contains(value1)) {
					return entryValue.getKey();
				}
			}
			return null;
		}

		private static String formRule(String rule, String value1, String value2) {
			if (!rule.isEmpty())
				rule += "^";

			rule += "(" + getAttributeName(value2) + ","
					+ getAction(value1, value2) + ")";
			return rule;
		}

		private static String getAction(String left, String right) {
			return left + " -> " + right;
		}

		private void printActionRule(final ArrayList<String> actionFrom,
				final ArrayList<String> actionTo,
				final ArrayList<String> actions, final String rule,
				final Mapper<LongWritable, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {

			final int support = calculateSupportLERS(actionTo, decisionTo);
			if (support != 0) {
				final String oldConfidence = String.valueOf((Integer
						.parseInt(calculateConfidenceLERS(actionFrom,
								decisionFrom))
						* Integer.parseInt(calculateConfidenceLERS(actionTo,
								decisionTo)) / 100));
				final String newConfidence = calculateConfidenceLERS(actionTo,
						decisionTo);

				if ((support >= minSupport && Double.parseDouble(oldConfidence) >= minConfidence)
						&& !oldConfidence.equals("0")
						&& !newConfidence.equals("0")) {

					if (actions != null) {

						Collections.sort(actions);
						if (!actionRulesArrayList.contains(actions)) {
							actionRulesArrayList.add(actions);

							try {
								Text key1 = new Text(rule + " ==> " + "("
										+ decisionAttribute + ","
										+ decisionFrom + " -> " + decisionTo
										+ ")");
								Text value1 = new Text(support + ":"
										+ newConfidence);
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

		}

		private void printExtraRules(final ArrayList<String> actionFrom,
				final ArrayList<String> actionTo,
				final Mapper<LongWritable, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {

			ArrayList<String> stableValues = getStableValues(actionTo);
			ArrayList<String> attributeValues = getAttributeValues(
					stableValues, decisionFrom, actionFrom);

			ArrayList<String> toBeAddedAttributes = getNewAttributes(
					actionFrom, actionTo, stableValues);
			ArrayList<String> tempAttributeValues;

			for (String attributeValue : toBeAddedAttributes) {
				tempAttributeValues = new ArrayList<String>();
				tempAttributeValues.add(attributeValue);

				ArrayList<String> checkList = getAttributeValues(stableValues,
						"", tempAttributeValues);

				if (attributeValues.containsAll(checkList)
						&& !checkList.isEmpty()) {
					String subRule = new String();
					ArrayList<String> subActionFrom = new ArrayList<String>();
					ArrayList<String> subActionTo = new ArrayList<String>();
					ArrayList<String> subActions = new ArrayList<String>();

					if (isStable(getAttributeName(attributeValue))) {
						subActionFrom.addAll(actionFrom);
						subActionFrom.add(attributeValue);

						subActionTo.addAll(actionTo);
						subActionTo.add(attributeValue);
					} else {
						subActionFrom = getSubActionFrom(actionFrom, actionTo,
								attributeValue);
						subActionTo.addAll(actionTo);
					}
					subRule = getSubRule(subActionFrom, subActionTo, subActions);

					try {
						printActionRule(subActionFrom, subActionTo, subActions,
								subRule, context);
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

		private ArrayList<String> getNewAttributes(
				ArrayList<String> actionFrom, ArrayList<String> actionTo,
				ArrayList<String> stableValues) {
			ArrayList<String> stableAttributes = new ArrayList<String>();
			ArrayList<String> flexibleAttributes = new ArrayList<String>();
			ArrayList<String> newAttributes = new ArrayList<String>();

			for (String string : stableValues) {
				stableAttributes.add(getAttributeName(string));
			}

			for (String string : actionTo) {
				flexibleAttributes.add(getAttributeName(string));
			}

			for (Map.Entry<String, HashSet<String>> mapValue : distinctAttributeValues
					.entrySet()) {
				String mapKey = mapValue.getKey();
				HashSet<String> mapValues = mapValue.getValue();

				if (mapKey.equals(decisionAttribute)
						|| (stableAttributes.size() != 0 && stableAttributes
								.contains(mapKey))) {
					continue;
				} else if (isStable(mapKey)) {
					newAttributes.addAll(mapValues);
				} else if (!flexibleAttributes.isEmpty()
						&& flexibleAttributes.contains(mapKey)) {

					for (String setValue : mapValues) {
						if (!actionFrom.contains(setValue)
								&& !actionTo.contains(setValue)) {
							newAttributes.add(setValue);
						}
					}
				}
			}

			return newAttributes;
		}

		private ArrayList<String> getStableValues(ArrayList<String> actionFrom) {
			ArrayList<String> stableValues = (ArrayList<String>) stableAttributeValuesList;
			ArrayList<String> toBeAdded = new ArrayList<String>();

			for (String value : actionFrom) {
				if (stableValues.contains(value)) {
					toBeAdded.add(value);
				} else {
					continue;
				}

			}

			return toBeAdded;
		}

		private ArrayList<String> getAttributeValues(
				ArrayList<String> stableValues, String decisionFrom,
				ArrayList<String> actionFrom) {
			ArrayList<String> temp = new ArrayList<String>();
			ArrayList<String> attributeValues = new ArrayList<String>();
			int lineCount = 0;

			temp.addAll(stableValues);

			for (String from : actionFrom) {
				if (!from.equals("")) {
					temp.add(from);
				}
			}

			if (!decisionFrom.equals(""))
				temp.add(decisionFrom);

			for (Map.Entry<ArrayList<String>, Integer> data1 : data.entrySet()) {
				lineCount++;

				if (data1.getKey().containsAll(temp)) {
					attributeValues.add("x" + lineCount);
				}
			}

			return attributeValues;
		}

		private ArrayList<String> getSubActionFrom(
				ArrayList<String> actionFrom, ArrayList<String> actionTo,
				String alternateActionFrom) {
			ArrayList<String> finalActionFrom = new ArrayList<String>();
			HashSet<String> checkSameSet;

			for (int i = 0; i < actionTo.size(); i++) {
				checkSameSet = new HashSet<String>();

				checkSameSet.add(alternateActionFrom);
				checkSameSet.add(actionTo.get(i));

				if (checkSameGroup(checkSameSet)) {
					finalActionFrom.add(alternateActionFrom);

				} else {
					if (i < actionFrom.size()) {
						finalActionFrom.add(actionFrom.get(i));
					}
				}

			}

			return finalActionFrom;
		}

		private String getSubRule(ArrayList<String> subActionFrom,
				ArrayList<String> subActionTo, ArrayList<String> subActions) {
			String rule = "";

			for (int i = 0; i < subActionFrom.size(); i++) {
				rule = formRule(rule, subActionFrom.get(i), subActionTo.get(i));
				subActions.add(getAction(subActionFrom.get(i),
						subActionTo.get(i)));
			}

			return rule;
		}

		public boolean isStable(String value) {
			if (stableAttributeValuesList.containsAll(distinctAttributeValues
					.get(value)))
				return true;
			else
				return false;
		}

	}

	public static class JobReducer extends Reducer<Text, Text, Text, Text> {

		@Override
		protected void reduce(Text key, Iterable<Text> values,
				Reducer<Text, Text, Text, Text>.Context context)
				throws IOException, InterruptedException {

			double support = 0, confidence = 0;
			DecimalFormat df = new DecimalFormat("###.##");
			int mapper = Integer.parseInt(context.getConfiguration().get(
					"mapred.map.tasks"));
			int min_presence = mapper / 2;
			int sum = 0;
			for (Text val : values) {
				sum++;
				support = support
						+ Double.valueOf(df.format(Double.parseDouble(val
								.toString().split(":")[0])));
				confidence = confidence
						+ Double.valueOf(df.format(Double.parseDouble(val
								.toString().split(":")[1])));
			}
			if (sum >= min_presence)
				context.write(
						key,
						new Text(sum + ""
								+ " [Support: "
								+ Double.parseDouble(df.format(support / sum))
								+ ", Confidence: "
								+ Double.parseDouble(df
										.format(confidence / sum)) + "%]"));
		}
	}
}