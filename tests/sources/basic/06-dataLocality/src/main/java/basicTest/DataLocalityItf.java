package basicTest;

import integratedtoolkit.types.annotations.Method;
import integratedtoolkit.types.annotations.Parameter;
import integratedtoolkit.types.annotations.Parameter.Direction;
import integratedtoolkit.types.annotations.Parameter.Type;


public interface DataLocalityItf {
    
    @Method(declaringClass = "basicTest.DataLocalityImpl")
    void task (
    	@Parameter(type = Type.INT, direction = Direction.IN) int id, 
    	@Parameter(type = Type.FILE, direction = Direction.INOUT) String fileName
    );
    
}
