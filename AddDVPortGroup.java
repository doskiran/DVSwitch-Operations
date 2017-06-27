package dvSwitchOperations;
//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Add dvPortgroup in the dvSwitch using vSphere java API. 
import java.net.URL;
import java.rmi.RemoteException;

import com.vmware.vim25.DVPortgroupConfigSpec;
import com.vmware.vim25.DistributedVirtualPortgroupPortgroupType;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;

public class AddDVPortGroup {

	/**
	 * This method is used to create dvportgroup in the specified dvSwitch
	 * @param si
	 * @param dvsName
	 * @param dvPortgroupName
	 * @return
	 */
	public boolean addDVPortGroupInDVS(ServiceInstance si, String dvsName,
			String dvPortgroupName) {
		DistributedVirtualSwitch dvSwitch = null;
		DVPortgroupConfigSpec cfgSpec = null;
		boolean status = false;
		try {
			if (si != null) {
				dvSwitch = (DistributedVirtualSwitch) new InventoryNavigator(
						si.getRootFolder()).searchManagedEntity(
						"DistributedVirtualSwitch", dvsName);
				if (dvSwitch != null) {
					cfgSpec = new DVPortgroupConfigSpec();
					cfgSpec.setName(dvPortgroupName);
					cfgSpec.setNumPorts(10);
					cfgSpec.setType(DistributedVirtualPortgroupPortgroupType.earlyBinding
							.toString());
					Task task_pg = dvSwitch
							.addDVPortgroup_Task(new DVPortgroupConfigSpec[] { cfgSpec });
					TaskInfo ti = waitFor(task_pg);
					if (ti.getState() == TaskInfoState.error) {
						System.out.println("Failed to add portgroup in DVS "
								+ dvsName);
						return false;
					}
					System.out
							.println("A new DVS port group has been created successfully..."
									+ dvPortgroupName);
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
		String dvSwitchName = "testDvs1"; // DVSwitch name
		try {
			ServiceInstance si = new ServiceInstance(new URL("https://"
					+ vcIPaddress + "/sdk"), userName, passwd, true);
			AddDVPortGroup obj = new AddDVPortGroup();
			obj.addDVPortGroupInDVS(si, dvSwitchName, "testDVPortgroup1");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
