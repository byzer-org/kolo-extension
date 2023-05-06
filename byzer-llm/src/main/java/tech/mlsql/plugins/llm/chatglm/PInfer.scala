package tech.mlsql.plugins.llm.chatglm

import org.apache.spark.sql.DataFrame
import streaming.dsl.ScriptSQLExec
import tech.mlsql.common.utils.log.Logging
import tech.mlsql.ets.{PythonCommand, Ray}

/**
 * 4/23/23 WilliamZhu(allwefantasy@gmail.com)
 */
class PInfer(params: Map[String, String]) extends Logging {
  def run(): DataFrame = {
    val session = ScriptSQLExec.context().execListener.sparkSession
    val localModelDir = params.getOrElse("localModelDir", "")
    val localPathPrefix = params.getOrElse("localPathPrefix", "/tmp")

    val finetuningType = params.getOrElse("finetuningType", "lora")

    val trainer = new Ray()

    val modelTable = params.getOrElse("modelTable", params.getOrElse("model", ""))
    require(modelTable.nonEmpty, "modelTable/model is required")
    val udfName = params("udfName")

    val devices = params.getOrElse("devices", "-1")
    val quantizationBit = params.getOrElse("quantizationBit", "false").toBoolean
    val quantizationBitNum = params.getOrElse("quantizationBitNum", "4")

    val maxLength = params.getOrElse("maxLength", "512")
    val topP = params.getOrElse("topP", "0.95")
    val temperature = params.getOrElse("temperature", "0.1")

    val quantizationBitCode = if (quantizationBit) {
      s"quantization_bit=${quantizationBitNum},"
    } else ""

    val code =
      s"""import os
         |if ${devices} != -1:
         |    os.environ["CUDA_VISIBLE_DEVICES"] = "${devices}"
         |try:
         |    import sys
         |    import logging
         |    import transformers
         |    import datasets
         |    logging.basicConfig(
         |    format="%(asctime)s - %(levelname)s - %(name)s - %(message)s",
         |    datefmt="%m/%d/%Y %H:%M:%S",
         |    handlers=[logging.StreamHandler(sys.stdout)],)
         |    transformers.utils.logging.set_verbosity_info()
         |    datasets.utils.logging.set_verbosity(logging.INFO)
         |    transformers.utils.logging.set_verbosity(logging.INFO)
         |    transformers.utils.logging.enable_default_handler()
         |    transformers.utils.logging.enable_explicit_format()
         |except ImportError:
         |    pass
         |
         |import ray
         |import numpy as np
         |from pyjava.api.mlsql import RayContext,PythonContext
         |
         |from pyjava.udf import UDFMaster,UDFWorker,UDFBuilder,UDFBuildInFunc
         |from typing import Any, NoReturn, Callable, Dict, List
         |import time
         |from ray.util.client.common import ClientActorHandle, ClientObjectRef
         |import uuid
         |import json
         |
         |import byzerllm.chatglm6b.tunning.infer as infer
         |from byzerllm.chatglm6b.finetune import restore_model,load_model
         |
         |from pyjava.storage import streaming_tar
         |
         |ray_context = RayContext.connect(globals(), context.conf["rayAddress"])
         |
         |rd=str(uuid.uuid4())
         |
         |MODEL_DIR=os.path.join("${localPathPrefix}",rd,"infer_model")
         |OUTPUT_DIR=os.path.join("${localPathPrefix}",rd,"checkpoint")
         |
         |if "${localModelDir}":
         |    MODEL_DIR="${localModelDir}"
         |if ${devices} != -1:
         |    os.environ["CUDA_VISIBLE_DEVICES"] = "${devices}"
         |
         |
         |def init_model(model_refs: List[ClientObjectRef], conf: Dict[str, str]) -> Any:
         |    if not "${localModelDir}":
         |      if "standalone" in conf and conf["standalone"]=="true":
         |          restore_model(conf,MODEL_DIR)
         |      else:
         |          streaming_tar.save_rows_as_file((ray.get(ref) for ref in model_refs),MODEL_DIR)
         |
         |    model = infer.init_model(MODEL_DIR)
         |    return model
         |
         |def predict_func(model,v):
         |    (trainer,tokenizer) = model
         |    data = [json.loads(item) for item in v]
         |    results=[{"predict":infer.predict(item["instruction"],trainer,tokenizer,max_length=${maxLength}, top_p=${topP},temperature=${temperature}),"labels":""} for item in data]
         |    return {"value":[json.dumps(results,ensure_ascii=False,indent=4)]}
         |
         |UDFBuilder.build(ray_context,init_model,predict_func)
         |""".stripMargin
    logInfo(code)
    trainer.predict(session, modelTable, udfName, Map(
      "registerCode" -> code,
      "sourceSchema" -> "st(field(value,string))",
      "outputSchema" -> "st(field(value,array(string)))"
    ) ++ params)
    session.emptyDataFrame
  }
}
