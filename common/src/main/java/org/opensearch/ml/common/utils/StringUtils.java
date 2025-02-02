/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensearch.OpenSearchParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class StringUtils {

    public static final String DEFAULT_ESCAPE_FUNCTION = "\n    String escape(def input) { \n"
        + "      if (input.contains(\"\\\\\")) {\n        input = input.replace(\"\\\\\", \"\\\\\\\\\");\n      }\n"
        + "      if (input.contains(\"\\\"\")) {\n        input = input.replace(\"\\\"\", \"\\\\\\\"\");\n      }\n"
        + "      if (input.contains('\r')) {\n        input = input = input.replace('\r', '\\\\r');\n      }\n"
        + "      if (input.contains(\"\\\\t\")) {\n        input = input.replace(\"\\\\t\", \"\\\\\\\\\\\\t\");\n      }\n"
        + "      if (input.contains('\n')) {\n        input = input.replace('\n', '\\\\n');\n      }\n"
        + "      if (input.contains('\b')) {\n        input = input.replace('\b', '\\\\b');\n      }\n"
        + "      if (input.contains('\f')) {\n        input = input.replace('\f', '\\\\f');\n      }\n"
        + "      return input;"
        + "\n    }\n";

    public static final Gson gson;

    static {
        gson = new Gson();
    }
    public static final String TO_STRING_FUNCTION_NAME = ".toString()";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static boolean isValidJsonString(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }

        try {
            new JSONObject(json);
        } catch (JSONException ex) {
            try {
                new JSONArray(json);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    public static boolean isJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }

        try {
            if (!isValidJsonString(json)) {
                return false;
            }
            // This is to cover such edge case "[]\""
            gson.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException ex) {
            return false;
        }
    }

    public static String toUTF8(String rawString) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(rawString);

        String utf8EncodedString = StandardCharsets.UTF_8.decode(buffer).toString();
        return utf8EncodedString;
    }

    public static Map<String, Object> fromJson(String jsonStr, String defaultKey) {
        Map<String, Object> result;
        JsonElement jsonElement = JsonParser.parseString(jsonStr);
        if (jsonElement.isJsonObject()) {
            result = gson.fromJson(jsonElement, Map.class);
        } else if (jsonElement.isJsonArray()) {
            List<Object> list = gson.fromJson(jsonElement, List.class);
            result = new HashMap<>();
            result.put(defaultKey, list);
        } else {
            throw new IllegalArgumentException("Unsupported response type");
        }
        return result;
    }

    public static Map<String, String> filteredParameterMap(Map<String, ?> parameterObjs, Set<String> allowedList) {
        Map<String, String> parameters = new HashMap<>();
        Set<String> filteredKeys = new HashSet<>(parameterObjs.keySet());
        filteredKeys.retainAll(allowedList);
        for (String key : filteredKeys) {
            Object value = parameterObjs.get(key);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    if (value instanceof String) {
                        parameters.put(key, (String) value);
                    } else {
                        parameters.put(key, gson.toJson(value));
                    }
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw new RuntimeException(e);
            }
        }
        return parameters;
    }

    @SuppressWarnings("removal")
    public static Map<String, String> getParameterMap(Map<String, ?> parameterObjs) {
        Map<String, String> parameters = new HashMap<>();
        for (String key : parameterObjs.keySet()) {
            Object value = parameterObjs.get(key);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    if (value instanceof String) {
                        parameters.put(key, (String) value);
                    } else {
                        parameters.put(key, gson.toJson(value));
                    }
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw new RuntimeException(e);
            }
        }
        return parameters;
    }

    @SuppressWarnings("removal")
    public static String toJson(Object value) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                if (value instanceof String) {
                    return (String) value;
                } else {
                    return gson.toJson(value);
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("removal")
    public static Map<String, String> convertScriptStringToJsonString(Map<String, Object> processedInput) {
        Map<String, String> parameterStringMap = new HashMap<>();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Map<String, Object> parametersMap = (Map<String, Object>) processedInput.getOrDefault("parameters", Map.of());
                for (String key : parametersMap.keySet()) {
                    if (parametersMap.get(key) instanceof String) {
                        parameterStringMap.put(key, (String) parametersMap.get(key));
                    } else {
                        parameterStringMap.put(key, gson.toJson(parametersMap.get(key)));
                    }
                }
                return null;
            });
        } catch (PrivilegedActionException e) {
            log.error("Error processing parameters", e);
            throw new RuntimeException(e);
        }
        return parameterStringMap;
    }

    public static List<String> processTextDocs(List<String> inputDocs) {
        List<String> docs = new ArrayList<>();
        for (String doc : inputDocs) {
            docs.add(processTextDoc(doc));
        }
        return docs;
    }

    public static String processTextDoc(String doc) {
        if (doc != null) {
            String gsonString = gson.toJson(doc);
            // in 2.9, user will add " before and after string
            // gson.toString(string) will add extra " before after string, so need to remove
            return gsonString.substring(1, gsonString.length() - 1);
        } else {
            return null;
        }
    }

    public static String addDefaultMethod(String functionScript) {
        if (!containsEscapeMethod(functionScript) && isEscapeUsed(functionScript)) {
            return DEFAULT_ESCAPE_FUNCTION + functionScript;
        }
        return functionScript;
    }

    public static boolean patternExist(String input, String patternString) {
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }

    public static boolean isEscapeUsed(String input) {
        return patternExist(input, "(?<!\\bString\\s+)\\bescape\\s*\\(");
    }

    public static boolean containsEscapeMethod(String input) {
        return patternExist(input, "String\\s+escape\\s*\\(\\s*(def|String)\\s+.*?\\)\\s*\\{?");
    }

    /**
     * This method will define if we should print out model id with the error message or not.
     * @param errorMessage
     * @param modelId
     * @param isHidden
     * @return
     */
    public static String getErrorMessage(String errorMessage, String modelId, Boolean isHidden) {
        if (BooleanUtils.isTrue(isHidden)) {
            return errorMessage;
        } else {
            return errorMessage + " Model ID: " + modelId;
        }
    }

    /**
     * Collects the prefixes of the toString() method calls present in the values of the given map.
     *
     * @param map A map containing key-value pairs where the values may contain toString() method calls.
     * @return A list of prefixes for the toString() method calls found in the map values.
     */
    public static List<String> collectToStringPrefixes(Map<String, String> map) {
        List<String> prefixes = new ArrayList<>();
        for (String key : map.keySet()) {
            String value = map.get(key);
            if (value != null) {
                Pattern pattern = Pattern.compile("\\$\\{parameters\\.(.+?)\\.toString\\(\\)\\}");
                Matcher matcher = pattern.matcher(value);
                while (matcher.find()) {
                    String prefix = matcher.group(1);
                    prefixes.add(prefix);
                }
            }
        }
        return prefixes;
    }

    /**
     * Parses the given parameters map and processes the values containing toString() method calls.
     *
     * @param parameters A map containing key-value pairs where the values may contain toString() method calls.
     * @return A new map with the processed values for the toString() method calls.
     */
    public static Map<String, String> parseParameters(Map<String, String> parameters) {
        if (parameters != null) {
            List<String> toStringParametersPrefixes = collectToStringPrefixes(parameters);

            if (!toStringParametersPrefixes.isEmpty()) {
                for (String prefix : toStringParametersPrefixes) {
                    String value = parameters.get(prefix);
                    if (value != null) {
                        parameters.put(prefix + TO_STRING_FUNCTION_NAME, processTextDoc(value));
                    }
                }
            }
        }
        return parameters;
    }

    public static String obtainFieldNameFromJsonPath(String jsonPath) {
        String[] parts = jsonPath.split("\\.");

        // Get the last part which is the field name
        return parts[parts.length - 1];
    }

    public static String getJsonPath(String jsonPathWithSource) {
        // Find the index of the first occurrence of "$."
        int startIndex = jsonPathWithSource.indexOf("$.");

        // Extract the substring from the startIndex to the end of the input string
        return (startIndex != -1) ? jsonPathWithSource.substring(startIndex) : jsonPathWithSource;
    }

    /**
     * Checks if the given input string matches the JSONPath format.
     *
     * <p>The JSONPath format is a way to navigate and extract data from JSON documents.
     * It uses a syntax similar to XPath for XML documents. This method attempts to compile
     * the input string as a JSONPath expression using the {@link com.jayway.jsonpath.JsonPath}
     * library. If the compilation succeeds, it means the input string is a valid JSONPath
     * expression.
     *
     * @param input the input string to be checked for JSONPath format validity
     * @return true if the input string is a valid JSONPath expression, false otherwise
     */
    public static boolean isValidJSONPath(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        try {
            JsonPath.compile(input); // This will throw an exception if the path is invalid
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonObject getJsonObjectFromString(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            throw new IllegalArgumentException("Json cannot be null or empty");
        }

        return JsonParser.parseString(jsonString).getAsJsonObject();
    }

    public static void validateSchema(String schemaString, String instanceString) {
        try {
            // parse the schema JSON as string
            JsonNode schemaNode = MAPPER.readTree(schemaString);
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);

            // JSON data to validate
            JsonNode jsonNode = MAPPER.readTree(instanceString);

            // Validate JSON node against the schema
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                String errorMessage = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining(", "));

                throw new OpenSearchParseException(
                    "Validation failed: " + errorMessage + " for instance: " + instanceString + " with schema: " + schemaString
                );
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new OpenSearchParseException("Schema validation failed: " + e.getMessage(), e);
        }
    }
}
