package dvSwitchOperations;

//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Add new LAG(Link Aggregation Group) in the existing dvSwitch. 
import java.net.URL;
import java.rmi.RemoteException;

import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VMwareDvsLacpGroupConfig;
import com.vmware.vim25.VMwareDvsLacpGroupSpec;
import com.vmware.vim25.VMwareDvsLacpLoadBalanceAlgorithm;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;

public class CreateLagOnDVS {

	/**
	 * This method is used to create LAG group in existing dvSwitch.
	 * 
	 * @param si
	 * @param dvsName
	 * @return
	 */
	public boolean createLag(ServiceInstance si, String dvsName) {
		boolean status = true;
		VmwareDistributedVirtualSwitch dvSwitch = null;
		try {
			if (si != null) {
				dvSwitch = (VmwareDistributedVirtualSwitch) new InventoryNavigator(
						si.getRootFolder()).searchManagedEntity(
						"DistributedVirtualSwitch", dvsName);
				if (dvSwitch != null) {
					VMwareDvsLacpGroupSpec lagGrpSpec = new VMwareDvsLacpGroupSpec();
					lagGrpSpec.setOperation("add");
					VMwareDvsLacpGroupConfig lacpGroupConf = new VMwareDvsLacpGroupConfig();
					lacpGroupConf.setName("LAG1");
					lacpGroupConf.setMode("active");
					lacpGroupConf.setUplinkNum(3);
					lacpGroupConf
							.setLoadbalanceAlgorithm(VMwareDvsLacpLoadBalanceAlgorithm.srcDestIpTcpUdpPortVlan
									.toString());
					lagGrpSpec.setLacpGroupConfig(lacpGroupConf);
					Task task_lag = dvSwitch
							.updateDVSLacpGroupConfig_Task(new VMwareDvsLacpGroupSpec[] { lagGrpSpec });
					TaskInfo ti = waitFor(task_lag);
					if (ti.getState() == TaskInfoState.error) {
						System.out.println("Failed to create lag group in DVS "
								+ dvsName);
						return false;
					}
					System.out
							.println("S uccessfully Lag group created in DVS ...");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return status;
	}

	public static TaskInfo waitFor(Task task) throws RemoteException,
			InterruptedException {
		while (true) {
			TaskInfo ti = task.getTaskInfo();
			TaskInfoState state = ti.getState();
			if (state == TaskInfoState.success || state == TaskInfoState.error) {
				return ti;
			}
			Thread.sleep(1000);
		}
	}

	public static void main(String[] args) {
		String vcIPaddress = "10.10.10.30"; // VC ipaddress/hostname
		String userName = "Administrator@vsphere.local"; // VC username
		String passwd = "vdfverfddsac"; // VC password
		String dvSwitchName = "DSwitch"; // DVSwitch name
		try {
			ServiceInstance si = new ServiceInstance(new URL("https://"
					+ vcIPaddress + "/sdk"), userName, passwd, true);
			CreateLagOnDVS obj = new CreateLagOnDVS();
			obj.createLag(si, dvSwitchName);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
