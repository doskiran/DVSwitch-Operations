//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Creates Distributed Virtual Switch in the specified datacenter using vSphere java API. 
//This dvSwitch will create 10 standalone dvports and with 4 uplinks("uplink1", "uplink2","uplink3", "uplink4")
import java.net.URL;

import com.vmware.vim25.DVSConfigSpec;
import com.vmware.vim25.DVSCreateSpec;
import com.vmware.vim25.DVSNameArrayUplinkPortPolicy;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;

public class CreateDVSwitch {

	/**
	 * This method creates dvSwitch in the specified datacenter. This dvSwitch
	 * will create 10 standalone dvports and with 4 uplinks("uplink1",
	 * "uplink2", "uplink3", "uplink4")
	 * 
	 * @param si
	 *            - ServiceInstance object
	 * @param dvsName
	 *            - Name of the dvSwitch
	 * @param dataCenterName
	 *            - Name of the datacenter
	 * @return
	 * @throws Exception
	 */
	public DistributedVirtualSwitch createDistributedVirtualSwitch(
			ServiceInstance si, String dvsName, String dataCenterName)
			throws Exception {
		DVSCreateSpec dvsCreateSpec = null;
		Datacenter dc = null;
		Folder networkFolder = null;
		DistributedVirtualSwitch dvSwitch = null;
		ManagedObjectReference dvsMor = null;
		try {
			if (si != null) {
				dc = (Datacenter) new InventoryNavigator(si.getRootFolder())
						.searchManagedEntity("Datacenter", dataCenterName);
				dvsCreateSpec = new DVSCreateSpec();
				DVSConfigSpec dvsConfigSpec = new DVSConfigSpec();
				dvsConfigSpec.setConfigVersion("");
				dvsConfigSpec.setName(dvsName);
				dvsConfigSpec.setDescription(dvsName);
				dvsConfigSpec.setNumStandalonePorts(10);
				String[] uplinkPortNames = { "uplink1", "uplink2", "uplink3",
						"uplink4" };
				DVSNameArrayUplinkPortPolicy uplinkPolicyInst = new DVSNameArrayUplinkPortPolicy();
				uplinkPolicyInst.setUplinkPortName(uplinkPortNames);
				dvsConfigSpec.setUplinkPortPolicy(uplinkPolicyInst);
				dvsCreateSpec.setConfigSpec(dvsConfigSpec);
				networkFolder = dc.getNetworkFolder();
				Task task = networkFolder.createDVS_Task(dvsCreateSpec);
				String waitForTask = task.waitForTask();
				System.out.println("Task...." + waitForTask);
				TaskInfo taskInfo = task.getTaskInfo();
				if ((taskInfo.getState()).equals(TaskInfoState.success)) {
					dvsMor = (ManagedObjectReference) taskInfo.getResult();
					dvSwitch = new DistributedVirtualSwitch(
							si.getServerConnection(), dvsMor);
					System.out
							.println("Distributed Virtual Switch created successfully::"
									+ dvSwitch.getName());
				} else {
					System.out
							.println("Distributed Virtual Switch creation failed message...."
									+ taskInfo.getError().getLocalizedMessage());
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return dvSwitch;
	}

	public static void main(String[] args) {
		String vcIPaddress = "10.10.10.1"; // VC ipaddress/hostname
		String userName = "Administrator@vsphere.local"; // VC username
		String passwd = "jsdhdjksjksdnuj"; // VC password
		try {
			ServiceInstance si = new ServiceInstance(new URL("https://"
					+ vcIPaddress + "/sdk"), userName, passwd, true);
			CreateDVSwitch obj = new CreateDVSwitch();
			DistributedVirtualSwitch dvsObj = obj
					.createDistributedVirtualSwitch(si, "dvs1", "vcqaDC");
		} catch (Exception e) {

		}
	}
}
