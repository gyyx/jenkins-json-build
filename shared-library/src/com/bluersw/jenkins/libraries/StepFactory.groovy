/*
* Copyright (C) MIT License
*
* @version 2.0
* @date 2020-8-15
*/
package com.bluersw.jenkins.libraries

import com.bluersw.jenkins.libraries.model.LogContainer
import com.bluersw.jenkins.libraries.model.LogType
import com.bluersw.jenkins.libraries.model.Step
import com.bluersw.jenkins.libraries.model.StepType
import com.bluersw.jenkins.libraries.model.Steps
import com.cloudbees.groovy.cps.NonCPS
import hudson.remoting.Channel
import com.bluersw.jenkins.libraries.utils.JSONExtend
import net.sf.json.JSONObject

/**
 * 构建过程配置工厂
 * @auther SunWeiSheng
 */
class StepFactory {

	/**
	 * json配置文件内定义构建步骤类型的节点类型的节点名称
	 */
	static final String STEP_TYPE_NODE_NAME = 'Type'
	/**
	 * json配置文件内全局变量节点名称
	 */
	static final String GLOBAL_VARIABLE_NODE_NAME = 'GlobalVariable'
	/**
	 * json配置文件内定义需要执行脚本的节点名称
	 */
	static final String COMMAND_SCRIPT_NODE_NAME = 'Script'
	/**
	 * json配置文件内全局变量中定义日志级别的节点名称
	 */
	static final String GLOBAL_LOG_LEVEL_NODE_NAME = 'LogLevel'
	/**
	 * 定义StepFactory类型对象加载时日志的结束标记
	 */
	static final String INIT_LOG_END_TAG = '初始化完成----------'
	/**
	 * 文件分割符号，用于跨系统转换路径
	 */
	private static final String FILE_SEPARATOR = System.getProperty("file.separator")
	String configPath
	String projectRoot
	private JSONExtend json
	private LinkedHashMap<String, Steps> stepsMap = new LinkedHashMap<>()
	private JSONObject jsonObject
	private Map<String, String> envVars


	/**
	 * 构造函数
	 * @param configPath 构建配置文件路径
	 * @param envVars Jenkins环境变量
	 */
	StepFactory(String configPath, Map<String, String> envVars) {
		this.configPath = configPath
		//设置Jenkins环境变量
		this.envVars = envVars == null ? new LinkedHashMap<String, String>() : envVars
		//设置项目目录要后于this.envVars的赋值，因为会改变this.envVars内容
		this.projectRoot = setProjectRoot(this.configPath)
		//加载配置文件
		this.json = new JSONExtend(Channel.current(), configPath, envVars)
		//获取经过变量赋值的配置文件JSON对象
		this.jsonObject = this.json.getJsonObject()

	}


	/**
	 * 根据全局变量属性名称返回属性值
	 * @param propertyName 属性名称
	 * @return 属性值
	 */
	String getGlobalVariableValue(String propertyName){
		if(this.stepsMap.containsKey(GLOBAL_VARIABLE_NODE_NAME))
		{
			return this.stepsMap[GLOBAL_VARIABLE_NODE_NAME].getStepsPropertyValue(propertyName)
		}
		else{
			return ''
		}
	}

	/**
	 * 获取全局变量信息
	 * @return 全局变量信息
	 */
	String getGlobalVariableInfo() {
		StringBuilder builder = new StringBuilder()
		builder.append('全局变量集合：\n')
		if (this.stepsMap.containsKey(GLOBAL_VARIABLE_NODE_NAME)) {
			this.stepsMap[GLOBAL_VARIABLE_NODE_NAME].getStepsProperty().each { builder.append("[${it.key}:${it.value}]\n") }
		}
		builder.append('全局变量输出完成')
		LogContainer.append(LogType.DEBUG, builder.toString())
		return builder.toString()
	}

	/**
	 * 获取指定构建集合名称的构建步骤集合及其包含构建步骤的JSON配置属性内容
	 * @param stepsName 构建步骤集合名称
	 * @return 构建步骤集合和该集合内的构建步骤属性内容
	 */
	String getStepsPropertyInfo(String stepsName) {
		StringBuilder builder = new StringBuilder()
		if (this.stepsMap.containsKey(stepsName)) {
			builder.append("[${this.stepsMap[stepsName].getStepsName()}] 节点属性:\n")
			for (Map.Entry entry in this.stepsMap[stepsName].getStepsProperty()) {
				builder.append("key:${entry.key} value:${entry.value}\n")
			}
			for (Step step in this.stepsMap[stepsName].getStepQueue()) {
				getStepPropertyInfo(step, builder)
			}
			builder.append("[${this.stepsMap[stepsName].getStepsName()}] 属性输出完成:")
		}
		else {
			builder.append("没有找到${stepsName}节点\n")
		}
		LogContainer.append(LogType.DEBUG, builder.toString())
		return builder.toString()
	}

	String getInitStartTag() {
		return this.configPath
	}

	static String getInitEndTag() {
		return INIT_LOG_END_TAG
	}

	/**
	 * 构建过程对象初始化
	 */
	void initialize() {
		//设置初始化日志开始标记
		LogContainer.append(LogType.INFO, this.getInitStartTag())
		LogContainer.append(LogType.INFO, '开始初始化..........')
		initializeSteps()
		LogContainer.append(LogType.INFO, '构建步骤初始化完成')
		perfectStepsProperty()
		LogContainer.append(LogType.INFO, '完善构建步骤属性')
		//如果日志等级小于等于DEBUG输出全局变量和构建步骤集合的属性信息
		if (getLogLevel() <= LogType.DEBUG) {
			getGlobalVariableInfo()
			this.stepsMap.keySet().each { getStepsPropertyInfo(it) }
		}

		//设置初始化日志的结束标记
		LogContainer.append(LogType.INFO, getInitEndTag())
	}

	/**
	 * 根据Steps名称返回Steps实例
	 * @param stepsName Steps名称
	 * @return Steps实例
	 */
	Steps getStepsByName(String stepsName) {
		if (this.stepsMap.containsKey(stepsName)) {
			return this.stepsMap[stepsName]
		}
		else {
			return null
		}
	}

	/**
	 * 获得全局变量中的日志级别
	 * @return 全局变量中的日志级别
	 */
	LogType getLogLevel() {
		if (this.stepsMap.containsKey(GLOBAL_VARIABLE_NODE_NAME)) {
			return Enum.valueOf(LogType.class, this.stepsMap[GLOBAL_VARIABLE_NODE_NAME].getStepsPropertyValue(GLOBAL_LOG_LEVEL_NODE_NAME))
		}
		else {
			return LogType.INFO
		}
	}

	/**
	 * 完善SonarQube对象属性
	 * @param step SonarQube对象
	 */
	private void perfectSonarQubeStep(Step step) {
		//设置ScannerScript属性的默认值
		if(step.getStepPropertyValue('ScannerScript') == ''){
			//在sonar-scanner之前进行mvn clean的目的在于过滤掉一些不必要检查的文件
			step.setStepProperty('ScannerScript', "cd ${this.projectRoot};sonar-scanner")
		}
		//设置ReportTaskPath属性的默认值
		if(step.getStepPropertyValue('ReportTaskPath') == '') {
			String path = "${this.projectRoot}/.scannerwork/report-task.txt"
			path = CrossPlatform(path)
			//这是SQ检查之后生成的本地报告的路径及文件
			step.setStepProperty('ReportTaskPath', path)
		}
		//设置Gate属性的默认值
		if(step.getStepPropertyValue('Gate') == '') {
			//这是判断SQ检查是否通过的标准，也是设置的阀门值，OK为最高，下面依次为WARN,、ERROR、NONE
			step.setStepProperty('Gate', 'OK')
		}
	}

	/**
	 * 对进行路径跨平台转换
	 * @param path 路径
	 * @return 根据操作系统转换
	 */
	private static String CrossPlatform(String path){
		path = path.replace('/',FILE_SEPARATOR)
		path = path.replace('\\',FILE_SEPARATOR)
		return path
	}

	/**
	 * 根据Json文件路径设置项目目录
	 * @param jsonPath Json文件路径
	 * @return 项目目录
	 */
	@NonCPS
	private String setProjectRoot(String jsonPath) {
		String projectRoot = jsonPath.substring(0, jsonPath.lastIndexOf(FILE_SEPARATOR) + 1)
		this.envVars.put('PWD', projectRoot)
		return projectRoot
	}

	/**
	 * 完善循环命令构建步骤
	 * @param step 循环命令构建步骤对象
	 */
	private static void perfectCommandForStep(Step step) {
		String forValue = step.getStepPropertyValue('For')
		String scriptTemplate = step.getStepPropertyValue('ScriptTemplate')
		//循环创建脚本
		if (forValue != '' && scriptTemplate != '') {
			String[] forArray = forValue.split(',')
			for (int i = 0; i < forArray.length; i++) {
				step.appendCommand("For-${forArray[i]}", scriptTemplate.replace('${loop-command-for}', forArray[i]))
			}
		}
	}

	/**
	 * 使用默认方法创建构建步骤对象
	 * @param stepName 构建步骤对象
	 * @param stepType 构建步骤类型
	 * @param jo 构建步骤对应的JSON对象
	 * @return 构建步骤对象
	 */
	private static Step defaultCreateStep(String stepName, StepType stepType, JSONObject jo) {
		Step step = new Step(stepName, stepType)
		Iterator<String> iterator = ((JSONObject) jo).entrySet().iterator()
		while (iterator.hasNext()) {
			Map.Entry entry = (Map.Entry) iterator.next()
			if (entry.value instanceof String) {
				//如果是字符串则添加属性
				step.setStepProperty(entry.key.toString(), entry.value.toString())
			}
			else if (entry.value instanceof JSONObject && entry.key.toString() == COMMAND_SCRIPT_NODE_NAME) {
				//如果有Script节点则循环创建命令对象
				Iterator<String> scriptIterator = ((JSONObject) entry.value).entrySet().iterator()
				while (scriptIterator.hasNext()) {
					Map.Entry scriptEntry = (Map.Entry) scriptIterator.next()
					step.appendCommand(scriptEntry.key.toString(), scriptEntry.value.toString())
				}
			}
		}
		return step
	}

	/**
	 * 创建构建步骤对象
	 * @param stepName 步骤名称
	 * @param o 构建步骤对应的JSON对象（也有可能不是）
	 * @return 构建步骤对象
	 */
	private Step createStep(String stepName, Object o) {
		Step step = null
		if (o instanceof JSONObject) {
			JSONObject stepNode = (JSONObject) o
			//检查构建步骤类型
			if (stepNode.containsKey(STEP_TYPE_NODE_NAME)) {
				//获取构建步骤类型
				StepType stepType = StepType.valueOf(stepNode.get(STEP_TYPE_NODE_NAME).toString())
				//使用默认方法创建构建步骤对象
				step = defaultCreateStep(stepName, stepType, stepNode)
				//根据构建步骤类型完善构建对象，这里可以增加不同类型节点的对象完善方法
				switch (step.stepType) {
				case (StepType.COMMAND_STATUS_FOR):
					perfectCommandForStep(step)
					break
				case (StepType.SONAR_QUBE):
					perfectSonarQubeStep(step)
					break
				case (StepType.JEST_COVERAGE_ANALYSIS):
					perfectJestStep(step)
					break
				case (StepType.LLVM_COV_COVERAGE_ANALYSIS):
					perfectLlvmCovStep(step)
					break
				case (StepType.MSBUILD_COVERAGE_ANALYSIS):
					perfectMSBuildCoverageStep(step)
					break
				case (StepType.JUNIT_PLUG_IN):
					perfectJunitStep(step)
					break
				default:
					break
				}
			}
		}
		return step
	}

	/**
	 * 完善使用Junit分析单元测试后的结果
	 * @param step 使用Junit分析单元测试后的结果步骤
	 */
	private static void perfectJunitStep(Step step){
		//设置JunitReportPath节点默认值
		if(step.getStepPropertyValue('JunitReportPath') == ''){
			step.setStepProperty('JunitReportPath',CrossPlatform('**/junitreports/TEST-*.xml'))
		}
	}

	/**
	 * 完善使用MSBuild构建后分析单元测试覆盖度步骤
	 * @param step 使用MSBuild构建后分析单元测试覆盖度步骤
	 */
	private void perfectMSBuildCoverageStep(Step step){
		//设置ReportDir节点默认值
		if(step.getStepPropertyValue('ReportDir') == ''){
			step.setStepProperty('ReportDir',CrossPlatform("${this.projectRoot}\\Cover\\"))
		}
	}

	/**
	 * 完善jest步骤
	 * @param step jest步骤
	 */
	private void perfectJestStep(Step step){
		//设置LcovReportDir节点默认值
		if(step.getStepPropertyValue('LcovReportDir') == ''){
			step.setStepProperty('LcovReportDir',CrossPlatform("${this.projectRoot}/coverage/lcov-report"))
		}
	}

	/**
	 * 完善llvm-cov步骤
	 * @param step llvm-cov步骤
	 */
	private void perfectLlvmCovStep(Step step){
		//设置XcodePathScript节点默认值
		if(step.getStepPropertyValue('XcodePathScript') == ''){
			step.setStepProperty('XcodePathScript','Xcode-select --print-path')
		}
		//设置LlvmCovCommand节点默认值
		if(step.getStepPropertyValue('LlvmCovCommand') == ''){
			step.setStepProperty('LlvmCovCommand','/Toolchains/XcodeDefault.xctoolchain/usr/bin/llvm-cov export -format=text --summary-only -instr-profile ')
		}
		//设置XcodeBuildLogPath节点默认值
		if(step.getStepPropertyValue('XcodeBuildLogPath') == ''){
			step.setStepProperty('XcodeBuildLogPath',"${this.projectRoot}/xcodebuild.log")
		}
	}

	/**
	 * 创建构建步骤集合（Steps）
	 * @param stepsName 集合名称
	 * @param o 步骤集合的JSON对象（也有可能不是）
	 * @return 构建步骤集合（Steps）
	 */
	private Steps createSteps(String stepsName, Object o) {
		//创建构建步骤集合
		Steps steps = new Steps(stepsName)
		if (o instanceof JSONObject) {
			Iterator<String> iterator = ((JSONObject) o).entrySet().iterator()
			//循环构建步骤集合的子节点
			while (iterator.hasNext()) {
				Map.Entry entry = (Map.Entry) iterator.next()
				if (entry.value instanceof String) {
					//如果是字符串就当成属性添加
					steps.setStepsProperty(entry.key.toString(), entry.value.toString())
				}
				else {//如果不是字符串就尝试创建构建步骤对象
					Step step = createStep(entry.key.toString(), entry.value)
					if (step != null) {
						steps.append(step)
					}
				}
			}
		}
		return steps
	}

	/**
	 * 根据构建配置创建和初始化对应的构建对象
	 */
	private void initializeSteps() {
		Iterator<String> iterator = this.jsonObject.entrySet().iterator()
		//循环配置文件JSON格式的第一层节点
		while (iterator.hasNext()) {
			Map.Entry entry = (Map.Entry) iterator.next()
			//因为默认第一层节点为步骤集合Steps，所以尝试创建构建步骤集合对象
			Steps steps = createSteps(entry.key.toString(), entry.value)
			//如果构建步骤集合中有内容（有效）或构建步骤是全局配置则加到Map中
			if (steps.isValid() || steps.getStepsName() == GLOBAL_VARIABLE_NODE_NAME) {
				this.stepsMap.put(entry.key.toString(), steps)
			}
		}
	}

	/**
	 * 完善构建集合的属性
	 */
	private void perfectStepsProperty() {
		Iterator<Map.Entry<String, Steps>> iterator = this.stepsMap.entrySet().iterator()
		while (iterator.hasNext()) {
			Map.Entry<String, Steps> entry = (Map.Entry<String, Steps>) iterator.next()
			Steps steps = entry.value
			//如果是全局变量
			if (steps.getStepsName() == GLOBAL_VARIABLE_NODE_NAME) {
				//设置全局变量的日志级别
				if (steps.getStepsPropertyValue(GLOBAL_LOG_LEVEL_NODE_NAME) == '') {
					steps.setStepsProperty(GLOBAL_LOG_LEVEL_NODE_NAME, LogType.INFO.toString())
				}
			}
			else {
				//设置非全局变量构建步骤集合的默认属性值
				//设置是否运行的属性
				if (steps.getStepsPropertyValue(Steps.IS_RUN_KEY_NAME) == '') {
					steps.setStepsProperty(Steps.IS_RUN_KEY_NAME, 'true')
				}
			}
		}
	}

	/**
	 * 获取构建步骤的JSON配置属性内容
	 * @param step 构建步骤
	 * @return 构建步骤属性内容
	 */
	private static void getStepPropertyInfo(Step step, StringBuilder builder) {
		builder.append("[${step.name}] 节点属性:\n")
		for (Map.Entry entry in step.getStepProperty()) {
			builder.append("key:${entry.key} value:${entry.value}\n")
		}
	}
}