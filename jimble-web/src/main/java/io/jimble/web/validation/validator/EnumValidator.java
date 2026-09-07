package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

import java.util.ArrayList;
import java.util.List;

/**
 * enum
 */
public class EnumValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/**
	 * enum
	 *
	 * @param enumType	enum
	 * @return	EnumValidator
	 */
	public EnumValidator enumType (Class<? extends Enum<?>> enumType) {

		this.settings.put("enum_type", enumType);

		// ログ用
		{
			List<String> enumList = new ArrayList<>();
			for (Object e : enumType.getEnumConstants()) {
				if (e instanceof Enum<?> eo) {
					enumList.add(eo.name());
				}
			}
			this.settings.put("enum_list", enumList);
		}

		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean validate(DB db, Data req, boolean isInsertRequest, Object value) throws CodeException {

		if (value == null) {
			return true;
		}

		String str = String.valueOf(value);
		if (str.isEmpty()) {
			return true;
		}

		try {
			Class<? extends Enum<?>> enumType = this.settings.getObject("enum_type");
			for (Object e : enumType.getEnumConstants()) {
				if (e instanceof Enum<?> eo) {
					if (eo.name().equals(str)) {
						return true;
					}
				}
			}
			return false;
		} catch (Exception ex) {
			return false;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Enum;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

}
