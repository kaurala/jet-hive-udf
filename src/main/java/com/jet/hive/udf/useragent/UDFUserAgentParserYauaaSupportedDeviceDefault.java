package com.jet.hive.udf.useragent;

import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.StringObjectInspector;
import org.apache.hadoop.io.Text;

import com.jet.utils.string.StringUtils;
import com.jet.utils.useragent.build.GooglePlayStoreSupportedDeviceBuild;
import com.jet.utils.useragent.constant.ConstantsUserAgent;
import com.jet.utils.useragent.utils.UserAgentAnalyzerYauaaSupportedDevicesUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Description(
    name = "UserAgentParse",
    value = "_FUNC_(str,[str],[str]) - Parses the UserAgent into a map type.\n" +
    "Required Env: Java 1.8+.",
    extended = "Example:\n" +
        "> SELECT UserAgentParse(useragent), \n" +
        "         UserAgentParse(useragent,'all'), \n" +
        "  FROM   useragent_table;\n" ) 
public class UDFUserAgentParserYauaaSupportedDeviceDefault extends GenericUDF {

	//private PrimitiveObjectInspector inputOI;
    private StringObjectInspector stringOI = null;
    private static UserAgentAnalyzer userAgentAnalyzer = null;
    private static GooglePlayStoreSupportedDeviceBuild supportedDevicesParser = null;
    private List<String> fieldNames = null;
    private boolean isNonInit = true; //未被init过

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length < 1) {
            throw new UDFArgumentException("The argument list must be exactly 1 or 2  element");
        }
        if (userAgentAnalyzer == null) {
            userAgentAnalyzer = UserAgentAnalyzer
                .newBuilder()
                .hideMatcherLoadStats()
                .delayInitialization()
                .build();
        }
        if (supportedDevicesParser == null) {
        	supportedDevicesParser = new GooglePlayStoreSupportedDeviceBuild();
        }
        
        // Initialize
        if (args.length == 1) {
        	this.fieldNames=getFieldNames(null);
        	this.isNonInit=false;
        }
		// 存储在全局变量的ObjectInspectors元素的输入
        ObjectInspector inputOI = args[0]; // The first argument must be a String
        if (!(inputOI instanceof StringObjectInspector)) {
            throw new UDFArgumentException("The argument must be a string");
        }
        stringOI = (StringObjectInspector) inputOI;
		// 返回变量输出类型
		return ObjectInspectorFactory.getStandardMapObjectInspector(  
                PrimitiveObjectInspectorFactory.writableStringObjectInspector,   
                PrimitiveObjectInspectorFactory.writableStringObjectInspector);  
    }


	@Override
    public Object evaluate(DeferredObject[] args) throws HiveException,UDFArgumentException {
        //根据第2个参数值，Initialize
        if(isNonInit){//未被初始化过，有2个参数的情况
        	if(args.length>1){ //根据第2个参数，得到map的key列表
        		String inputFields = stringOI.getPrimitiveJavaObject(args[1].get());
        		this.fieldNames=getFieldNames(inputFields);
        	}
        	this.isNonInit=false;
        }

        String userAgentString = stringOI.getPrimitiveJavaObject(args[0].get());
        if (StringUtils.isBlank(userAgentString)) {
            return null;
        }
        
        Map<Text, Text> reMap = new HashMap<Text, Text>();
        Map<String, String> uavs = UserAgentAnalyzerYauaaSupportedDevicesUtils.getVaulesByParser(userAgentAnalyzer, supportedDevicesParser, userAgentString, fieldNames);
        for (String key : uavs.keySet()) {
        	String value = uavs.get(key);
        	Text t = ( value==null?null:new Text(value) );
        	reMap.put(new Text(key), t);
		}
        return reMap;
    }

    @Override
    public String getDisplayString(String[] args) {
        return "Parses the UserAgent into all possible pieces.";
    }
    
    /**
     * 返回输入列名的List类型值
     * @param inputFields 形为col1,col2,...,colN
     * @return
     * @throws UDFArgumentException 
     */
    private List<String> getFieldNames(String inputFields) throws UDFArgumentException {
    	List<String> re=null;
    	List<String> fieldNamesNative = userAgentAnalyzer.getAllPossibleFieldNamesSorted();//原工具的所有展出的列名
    	fieldNamesNative.add(ConstantsUserAgent.YAUAA_DEVICE_MODEL_NAME);
		fieldNamesNative.add(ConstantsUserAgent.YAUAA_DEVICE_VENDOR);
    	//处理入参列名，支持default或all或c1,c2,c3
		
		if(StringUtils.isBlank(inputFields)){
			inputFields=ConstantsUserAgent.YAUAA_FIELD_DEFAULT;
		}
		if(inputFields.equals(ConstantsUserAgent.YAUAA_FIELD_ALL)){
			
			re = fieldNamesNative;
    	}else {
    		if(inputFields.equals(ConstantsUserAgent.YAUAA_FIELD_DEFAULT)){
    			inputFields=ConstantsUserAgent.YAUAA_FIELD_DEFAULT_FIELD_NAMES;
    			}
    		String[] es = inputFields.split(ConstantsUserAgent.FIELD_SPLIT_BY);
    		List<String> fieldNames=new ArrayList<String>();
    		for (String e : es) {
    			//if(fieldNamesNative.contains(e.trim()))
    				fieldNames.add(e.trim());
			}
    		re = fieldNames;
    	}
		
		// re为返回结果，下面为check合法
		Set<String> inputSet = new HashSet<String>();
		Set<String> inputSet2 = new HashSet<String>();
		Set<String> needsSet = new HashSet<String>();
		for (String string : re) {
			inputSet.add(string);
			inputSet2.add(string);
		}
		List<String> needs = Arrays.asList(ConstantsUserAgent.YAUAA_FIELD_NEED.split(ConstantsUserAgent.FIELD_SPLIT_BY));
		for (String string : needs) {
			needsSet.add(string);
		}
		inputSet.removeAll(fieldNamesNative);
		needsSet.removeAll(re);
		if(inputSet.size()==0 && needsSet.size()==0){
			return re;
		}else if(inputSet.size()>=0){
			throw new UDFArgumentException(String.format("The 2nd argument must be a string, and values in [%s]", ConstantsUserAgent.YAUAA_FIELD_ALL));
		}else if(needsSet.size()>=0){
			throw new UDFArgumentException(String.format("The 2nd argument must be a string, and values must contain [%s]", ConstantsUserAgent.YAUAA_FIELD_NEED));
		}
		else{
			throw new UDFArgumentException("The 2nd argument must be a string");
		}
	}
    
    
}
