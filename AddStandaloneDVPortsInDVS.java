
//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Add specified number of standalone dvPorts in the dvSwitch using vSphere java API. 
//Eg: current=100, new=150 ==> 50 new standalone DVPorts are created.
import java.net.URL;

import com.vmware.vim25.DVSConfigInfo;
import com.vmware.vim25.DVSConfigSpec;
import com.vmware.vim25.DistributedVirtualSwitchPortCriteria;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;

public class AddStandaloneDVPortsInDVS {
	/**
	 * This method used to add standalone dvports in the dvSwitch.
	 * 
	 * @param si
	 *            - ServiceInstance
	 * @param dvsName
	 *            - DVSwitch name
	 * @param noOfStandalonePorts
	 *            - Number of standalone dvPorts to add into dvSwitch
	 * @return
	 * @throws Exception
	 */
	public boolean addStandaloneDVPorts(ServiceInstance si, String dvsName,
			int noOfStandalonePorts) throws Exception {
		DistributedVirtualSwitch dvSwitch = null;
		DistributedVirtualSwitchPortCriteria portCriteria = null;
		DVSConfigSpec spec = null;
		boolean status = false;
		try {
			if (si != null) {
				dvSwitch = (DistributedVirtualSwitch) new InventoryNavigator(
						si.getRootFolder()).searchManagedEntity(
						"DistributedVirtualSwitch", dvsName);
				if (dvSwitch != null) {
					DVSConfigInfo info = dvSwitch.getConfig();
					portCriteria = new DistributedVirtualSwitchPortCriteria();
					portCriteria.setInside(false);
					spec = new DVSConfigSpec();
					// Eg: current=100, new=150 ==> 50 new standalone DVPorts are
					// created.
					spec.setNumStandalonePorts(info.getNumStandalonePorts()
							+ noOfStandalonePorts);
					spec.setConfigVersion(info.getConfigVersion());
					Task task = dvSwitch.reconfigureDvs_Task(spec);
					String waitForTask = task.waitForTask();
					System.out.println("Task...." + waitForTask);
					TaskInfo taskInfo = task.getTaskInfo();
					if ((taskInfo.getState()).equals(TaskInfoState.success)) {
						System.out
								.println("Added standalone dvPorts successfully::"
										+ dvSwitch.getName());
						status = true;
					} else {
						System.out.println("Unable to create new DVPorts::"
								+ taskInfo.getError().getLocalizedMessage());
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return status;
	}

	public static void main(String[] args) {
		String vcIPaddress = "10.10.10.1"; // VC ipaddress/hostname
		String userName = "Administrator@vsphere.local"; // VC username
		String passwd = "jsdhdjksjksdnuj"; // VC password
		try {
			ServiceInstance si = new ServiceInstance(new URL("https://"
					+ vcIPaddress + "/sdk"), userName, passwd, true);
			AddStandaloneDVPortsInDVS obj = new AddStandaloneDVPortsInDVS();
			boolean status = obj.addStandaloneDVPorts(si, "dvs1", 100);
			System.out.println("Added standalone dvPorts --->"+status);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
