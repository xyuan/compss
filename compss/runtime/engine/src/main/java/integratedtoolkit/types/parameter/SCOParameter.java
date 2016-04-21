package integratedtoolkit.types.parameter;

import integratedtoolkit.api.ITExecution.ParamDirection;
import integratedtoolkit.api.ITExecution.ParamType;

public class SCOParameter extends DependencyParameter {

	private int code;
	private Object value;

	public SCOParameter(ParamType type, ParamDirection direction, Object value, int code) {
		 super(type, direction);
		 this.setValue(value);
		 this.setCode(code);
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}
	
	public String toString() {
        return "SCO with hash code " + getCode();
    }

}
