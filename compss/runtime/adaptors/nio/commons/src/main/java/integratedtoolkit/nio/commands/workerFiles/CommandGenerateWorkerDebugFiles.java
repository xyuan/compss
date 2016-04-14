package integratedtoolkit.nio.commands.workerFiles;

import integratedtoolkit.nio.commands.Command;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import es.bsc.comm.Connection;


public class CommandGenerateWorkerDebugFiles extends Command implements Externalizable {

	private String host;
	private String installDir;
	private String workingDir;
	private String name;

	public CommandGenerateWorkerDebugFiles() {
		super();
	}
	
	public CommandGenerateWorkerDebugFiles(String host, String installDir, String workingDir, String name) {
		super();
		this.host = host;
		this.installDir = installDir;
		this.workingDir = workingDir;
		this.name = name;
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		host = (String)in.readObject();
		installDir = (String)in.readObject();
		workingDir = (String)in.readObject();
		name = (String)in.readObject();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(host);
		out.writeObject(installDir);
		out.writeObject(workingDir);
		out.writeObject(name);
	}

	@Override
	public CommandType getType() {
		return CommandType.GEN_WORKERS_INFO;
	}

	@Override
	public void handle(Connection c) {
		agent.generateWorkersDebugInfo(c);
	}

}
