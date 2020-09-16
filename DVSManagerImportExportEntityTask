import java.net.URL;
import java.rmi.RemoteException;

import com.vmware.vim25.ArrayOfEntityBackupConfig;
import com.vmware.vim25.DVPortgroupSelection;
import com.vmware.vim25.DVSSelection;
import com.vmware.vim25.DistributedVirtualSwitchManagerImportResult;
import com.vmware.vim25.EntityBackupConfig;
import com.vmware.vim25.SelectionSet;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;

//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Program to Export and Import DVS using vSphere java API. 
public class DVSImportExportTask {

	public EntityBackupConfig[] exportDVSEntity(ServiceInstance si, String dvsName) {
		DistributedVirtualSwitch dvSwitch = null;
		String dvsUUID = null;
		EntityBackupConfig[] dvsBackupConfig = null;
		try {
			if (si != null) {
				dvSwitch = (DistributedVirtualSwitch) new InventoryNavigator(si.getRootFolder())
						.searchManagedEntity("DistributedVirtualSwitch", dvsName);
				if (dvSwitch != null) {
					dvsUUID = dvSwitch.getUuid();
					DistributedVirtualPortgroup[] dvPGs = dvSwitch.getPortgroup();
					String[] dvpgKeys = new String[dvPGs.length];
					SelectionSet[] selectionSet = new SelectionSet[2];
					DVSSelection dvsSelection = new DVSSelection();
					dvsSelection.setDvsUuid(dvsUUID);
					selectionSet[0] = dvsSelection;
					DVPortgroupSelection dvpgSelection = new DVPortgroupSelection();
					dvpgSelection.setDvsUuid(dvsUUID);
					for (int i = 0; i < dvPGs.length; i++) {
						dvpgKeys[i] = dvPGs[i].getKey();
					}
					dvpgSelection.setPortgroupKey(dvpgKeys);
					selectionSet[1] = dvpgSelection;
					Task exportTask = si.getDistributedVirtualSwitchManager().dVSManagerExportEntity_Task(selectionSet);
					TaskInfo ti = waitFor(exportTask);
					if (ti.getState() == TaskInfoState.error) {
						System.err.println("Error:: Failed to export DVS and its portgroups..." + dvsName);
					} else {
						System.out.println("Successfully exported DVS and its portgroups..." + dvsName);
						ArrayOfEntityBackupConfig config = (ArrayOfEntityBackupConfig) exportTask.getTaskInfo()
								.getResult();
						dvsBackupConfig = config.getEntityBackupConfig();
						System.out.println(dvsBackupConfig.length);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dvsBackupConfig;
	}

	public DistributedVirtualSwitchManagerImportResult importDVSEntity(ServiceInstance si,
			EntityBackupConfig[] dvsBackupConfig, String importType) {
		DistributedVirtualSwitchManagerImportResult dvsImportResult = null;
		try {
			if (si != null && dvsBackupConfig != null) {
				Task importTask = si.getDistributedVirtualSwitchManager().dVSManagerImportEntity_Task(dvsBackupConfig,
						importType);
				TaskInfo ti = waitFor(importTask);
				if (ti.getState() == TaskInfoState.error) {
					System.err.println("Error:: Failed to import DVS and its portgroups...");
				} else {
					System.out.println("Successfully import DVS and its portgroups...");
					dvsImportResult = (DistributedVirtualSwitchManagerImportResult) importTask.getTaskInfo()
							.getResult();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dvsImportResult;
	}

	public static TaskInfo waitFor(Task task) throws RemoteException, InterruptedException {
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
		String vcIPaddress = "10.10.10.123"; // VC ipaddress/hostname
		String userName = "Administrator@vsphere.local"; // VC username
		String passwd = "cmiocmwcw"; // VC password
		String dvSwitchName = "DSwitch"; // DVSwitch name
		try {
			DVSImportExportTask task = new DVSImportExportTask();
			ServiceInstance si = new ServiceInstance(new URL("https://" + vcIPaddress + "/sdk"), userName, passwd,
					true);
			EntityBackupConfig[] dvsBackupConfig = task.exportDVSEntity(si, dvSwitchName);
			// importType = createEntityWithOriginalIdentifier or
			// createEntityWithNewIdentifier or applyToEntitySpecified
			DistributedVirtualSwitchManagerImportResult dvsImportResult = task.importDVSEntity(si, dvsBackupConfig,
					"createEntityWithNewIdentifier");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
