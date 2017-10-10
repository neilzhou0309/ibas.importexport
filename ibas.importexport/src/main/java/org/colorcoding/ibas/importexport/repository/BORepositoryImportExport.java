package org.colorcoding.ibas.importexport.repository;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.colorcoding.ibas.bobas.bo.IBusinessObject;
import org.colorcoding.ibas.bobas.common.ICriteria;
import org.colorcoding.ibas.bobas.common.IOperationResult;
import org.colorcoding.ibas.bobas.common.OperationResult;
import org.colorcoding.ibas.bobas.core.BOFactory;
import org.colorcoding.ibas.bobas.core.IBusinessObjectBase;
import org.colorcoding.ibas.bobas.core.RepositoryException;
import org.colorcoding.ibas.bobas.data.FileData;
import org.colorcoding.ibas.bobas.i18n.I18N;
import org.colorcoding.ibas.bobas.repository.BORepositoryServiceApplication;
import org.colorcoding.ibas.bobas.serialization.ISerializer;
import org.colorcoding.ibas.bobas.serialization.SerializerFactory;
import org.colorcoding.ibas.importexport.bo.dataexporttemplate.DataExportTemplate;
import org.colorcoding.ibas.importexport.bo.dataexporttemplate.IDataExportTemplate;
import org.colorcoding.ibas.importexport.transformer.IFileTransformer;
import org.colorcoding.ibas.importexport.transformer.ITransformer;
import org.colorcoding.ibas.importexport.transformer.TransformerFactory;

/**
 * ImportExport仓库
 */
public class BORepositoryImportExport extends BORepositoryServiceApplication
		implements IBORepositoryImportExportSvc, IBORepositoryImportExportApp {

	// --------------------------------------------------------------------------------------------//

	/**
	 * 导入数据
	 * 
	 * @param data
	 *            数据
	 * @param token
	 *            口令
	 * @return 操作结果
	 */
	public OperationResult<String> importData(FileData data, String token) {
		OperationResult<String> opRslt = null;
		boolean myTrans = false;
		try {
			this.setUserToken(token);
			if (data == null || data.getOriginalName().indexOf(".") < 0) {
				throw new Exception(I18N.prop("msg_importexport_invaild_file_data"));
			}
			// 创建转换者
			String type = String.format(TransformerFactory.GROUP_FILE_TO,
					data.getOriginalName().substring(data.getOriginalName().indexOf(".") + 1)).toUpperCase();
			ITransformer<?, ?> transformer = TransformerFactory.create().create(type);
			if (!(transformer instanceof IFileTransformer)) {
				throw new Exception(I18N.prop("msg_importexport_not_found_transformer", type));
			}
			IFileTransformer fileTransformer = (IFileTransformer) transformer;
			// 转换文件数据到业务对象
			fileTransformer.setInputData(new File(data.getLocation()));
			fileTransformer.transform();
			myTrans = this.beginTransaction();
			opRslt = new OperationResult<String>();
			// 保存业务对象
			for (IBusinessObject object : fileTransformer.getOutputData()) {
				IOperationResult<IBusinessObject> opRsltSave = this.save((IBusinessObject) object, token);
				if (opRsltSave.getError() != null) {
					throw opRsltSave.getError();
				}
				if (opRsltSave.getResultCode() != 0) {
					throw new Exception(opRsltSave.getMessage());
				}
				IBusinessObjectBase bo = opRsltSave.getResultObjects().firstOrDefault();
				if (bo != null) {
					opRslt.addResultObjects(bo.toString());
				}
			}
			if (myTrans) {
				this.commitTransaction();
			}
		} catch (Exception e) {
			opRslt = new OperationResult<String>(e);
			if (myTrans) {
				try {
					this.rollbackTransaction();
				} catch (RepositoryException e1) {
					opRslt.setMessage(opRslt.getMessage() + "&" + e1.getMessage());
				}
			}
		}
		return opRslt;
	}

	/**
	 * 导入数据
	 * 
	 * @param data
	 *            数据
	 * @return 操作结果
	 */
	public IOperationResult<String> importData(FileData data) {
		return this.importData(data, this.getUserToken());
	}

	// --------------------------------------------------------------------------------------------//
	/**
	 * 查询-数据导出模板
	 * 
	 * @param criteria
	 *            查询
	 * @param token
	 *            口令
	 * @return 操作结果
	 */
	public OperationResult<DataExportTemplate> fetchDataExportTemplate(ICriteria criteria, String token) {
		return super.fetch(criteria, token, DataExportTemplate.class);
	}

	/**
	 * 查询-数据导出模板（提前设置用户口令）
	 * 
	 * @param criteria
	 *            查询
	 * @return 操作结果
	 */
	public IOperationResult<IDataExportTemplate> fetchDataExportTemplate(ICriteria criteria) {
		return new OperationResult<IDataExportTemplate>(this.fetchDataExportTemplate(criteria, this.getUserToken()));
	}

	/**
	 * 保存-数据导出模板
	 * 
	 * @param bo
	 *            对象实例
	 * @param token
	 *            口令
	 * @return 操作结果
	 */
	public OperationResult<DataExportTemplate> saveDataExportTemplate(DataExportTemplate bo, String token) {
		return super.save(bo, token);
	}

	/**
	 * 保存-数据导出模板（提前设置用户口令）
	 * 
	 * @param bo
	 *            对象实例
	 * @return 操作结果
	 */
	public IOperationResult<IDataExportTemplate> saveDataExportTemplate(IDataExportTemplate bo) {
		return new OperationResult<IDataExportTemplate>(
				this.saveDataExportTemplate((DataExportTemplate) bo, this.getUserToken()));
	}

	// --------------------------------------------------------------------------------------------//
	@Override
	public IOperationResult<String> schema(String boCode, String type) {
		return this.schema(boCode, type, this.getUserToken());
	}

	@Override
	public OperationResult<String> schema(String boCode, String type, String token) {
		OperationResult<String> opRslt = new OperationResult<String>();
		try {
			this.setUserToken(token);
			Class<?> boType = BOFactory.create().getBOClass(boCode);
			if (boType == null) {
				throw new Exception(I18N.prop("msg_importexport_not_found_class", boCode));
			}
			ISerializer<?> serializer = SerializerFactory.create().createManager().create(type);
			if (serializer == null) {
				throw new Exception(I18N.prop("msg_importexport_not_found_serializer", type));
			}
			ByteArrayOutputStream writer = new ByteArrayOutputStream();
			serializer.getSchema(boType, writer);
			opRslt.addResultObjects(writer.toString());
		} catch (Exception e) {
			opRslt = new OperationResult<String>(e);
		}
		return opRslt;
	}

	// --------------------------------------------------------------------------------------------//

}
