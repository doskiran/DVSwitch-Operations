//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Adds specified number of ESXi hosts into dvSwitch using vSphere java API. 
//Here it will find the first free pnic from each ESXi host and then add it to Uplink1 otherwise it will skip that host.
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.vmware.vim25.ConfigSpecOperation;
import com.vmware.vim25.DVSConfigSpec;
import com.vmware.vim25.DVSNameArrayUplinkPortPolicy;
import com.vmware.vim25.DistributedVirtualSwitchHostMemberConfigSpec;
import com.vmware.vim25.DistributedVirtualSwitchHostMemberPnicBacking;
import com.vmware.vim25.DistributedVirtualSwitchHostMemberPnicSpec;
import com.vmware.vim25.HostNetworkInfo;
import com.vmware.vim25.HostProxySwitch;
import com.vmware.vim25.HostVirtualSwitch;
import com.vmware.vim25.PhysicalNic;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.HostNetworkSystem;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;

/**
 *Input & Steps::
 *1) Provide the dvSwitch name(in dvsName) and list of host to add (in hostNames) -- addHostsintoDVS(dvsName,hostNames)
 *2) Create DVSwitch object
 *3) For each host find the free physical nics.(it will find the free pnincs from host vSwitch & proxy switch)
 *4) Associate first free pnic to the the dvs Uplink1
 *5) ReconfigureDvs to add above free pnic to the dvs Uplink1. -- reconfigureDvs_Task(dvsConfigSpec)
 *
 */
public class AddHostsintoDVSwitch {
	/**
	 * This method is used to add the hosts into the DVSwitch, it will find the
	 * first free pnic from each host and add it to Uplink1 port
	 * 
	 * @param si
	 *            - ServiceInstance
	 * @param dvsName
	 *            - DVSwitch name
	 * @param hostNames
	 *            - List of hosts to add
	 * @return
	 * @throws Exception
	 */
	public boolean addHostsintoDVS(ServiceInstance si, String dvsName,
			List<String> hostNames) throws Exception {

		DistributedVirtualSwitch dvSwitch = null;
		boolean status = false;
		List<DistributedVirtualSwitchHostMemberConfigSpec> hostMemberConfigSpecList = new ArrayList<DistributedVirtualSwitchHostMemberConfigSpec>();
		try {
			if (si != null) {
				dvSwitch = (DistributedVirtualSwitch) new InventoryNavigator(
						si.getRootFolder()).searchManagedEntity(
						"DistributedVirtualSwitch", dvsName);
				if (dvSwitch != null) {
					for (String hostName : hostNames) {
						HostSystem hs = (HostSystem) new InventoryNavigator(
								si.getRootFolder()).searchManagedEntity(
								"HostSystem", hostName);
						// Get the free physical nics from the host
						String[] freePnics = getFreePnics(hs);
						if ((freePnics != null) && (freePnics.length > 0)) {
							DistributedVirtualSwitchHostMemberConfigSpec hostMemberconfigSpec = new DistributedVirtualSwitchHostMemberConfigSpec();
							DistributedVirtualSwitchHostMemberPnicBacking pnicBacking = new DistributedVirtualSwitchHostMemberPnicBacking();
							DistributedVirtualSwitchHostMemberPnicSpec pnicSpec = new DistributedVirtualSwitchHostMemberPnicSpec();
							List<DistributedVirtualSwitchHostMemberPnicSpec> pnicSpecList = new ArrayList<DistributedVirtualSwitchHostMemberPnicSpec>();
							hostMemberconfigSpec.setHost(hs.getMOR());
							hostMemberconfigSpec
									.setOperation(ConfigSpecOperation.add
											.toString());
							// Adding first host free pnic to dvs uplink
							pnicSpec.setPnicDevice(freePnics[0]);
							pnicSpecList.add(pnicSpec);
							DistributedVirtualSwitchHostMemberPnicSpec[] pnicSpecArr = pnicSpecList
									.toArray(new DistributedVirtualSwitchHostMemberPnicSpec[pnicSpecList
											.size()]);
							pnicBacking.setPnicSpec(pnicSpecArr);
							hostMemberconfigSpec.setBacking(pnicBacking);
							hostMemberConfigSpecList.add(hostMemberconfigSpec);
						}
					}
					DVSConfigSpec dvsConfigSpec = new DVSConfigSpec();
					dvsConfigSpec.setConfigVersion(dvSwitch.getConfig()
							.getConfigVersion());
					// Assigning first free pnic to the DVS Uplink1
					String[] uplinkPortNames = new String[] { "Uplink1" };
					DVSNameArrayUplinkPortPolicy uplinkPolicyInst = new DVSNameArrayUplinkPortPolicy();
					uplinkPolicyInst.setUplinkPortName(uplinkPortNames);
					dvsConfigSpec.setUplinkPortPolicy(uplinkPolicyInst);

					DistributedVirtualSwitchHostMemberConfigSpec[] hostMemberConfigSpecArr = hostMemberConfigSpecList
							.toArray(new DistributedVirtualSwitchHostMemberConfigSpec[hostMemberConfigSpecList
									.size()]);
					dvsConfigSpec.setHost(hostMemberConfigSpecArr);
					Task task = dvSwitch.reconfigureDvs_Task(dvsConfigSpec);
					String waitForTask = task.waitForTask();
					System.out.println("Task...." + waitForTask);
					TaskInfo taskInfo = task.getTaskInfo();
					if ((taskInfo.getState()).equals(TaskInfoState.success)) {
						System.out.println("Added ESXi servers successfully::"
								+ dvSwitch.getName());
						status = true;
					} else {
						System.out.println("Unable to add ESXi servers::"
								+ taskInfo.getError().getLocalizedMessage());
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return status;

	}

	/**
	 * This method is used to get the free(unused) physical adapters from Host.
	 * 
	 * @param hs
	 * @return
	 */
	public String[] getFreePnics(HostSystem hs) {
		String pnicIds[] = null;
		String usedPnics[] = null;
		try {

			HostNetworkSystem hostNS = hs.getHostNetworkSystem();
			HostNetworkInfo info = hostNS.getNetworkInfo();
			PhysicalNic[] pnics = info.getPnic();
			List<String> pnicsList = null;
			DistributedVirtualSwitchHostMemberPnicBacking pnicBacking = null;
			if (pnics != null && pnics.length > 0) {
				final int len = pnics.length;
				pnicsList = new ArrayList<String>();
				for (int i = 0; i < len; i++) {
					pnicsList.add(pnics[i].getDevice());
				}
				// Note:: Below 2 operation are used to get the host free pnics
				// from the HostVirtualSwitch & HostProxySwitch(dvSwitch).
				// In the same way you need to check and get
				// the free pnics from the HostOpaqueSwitch.

				HostVirtualSwitch[] vswitches = info.getVswitch();
				if (vswitches != null) {
					for (int i = 0; i < vswitches.length; i++) {
						if (vswitches[i] != null) {
							usedPnics = vswitches[i].getPnic();
							if (usedPnics != null) {
								for (int j = 0; j < usedPnics.length; j++) {
									for (int k = 0; k < pnicsList.size(); k++) {
										if (usedPnics[j].indexOf(pnicsList
												.get(k)) >= 0) {
											pnicsList.remove(k);
										}
									}
								}
							}
						}
					}
				}

				HostProxySwitch[] hostProxySwitchArr = info.getProxySwitch();
				if (hostProxySwitchArr != null) {
					for (HostProxySwitch hostProxySwitch : hostProxySwitchArr) {
						if (hostProxySwitch != null
								&& hostProxySwitch.getSpec() != null
								&& hostProxySwitch.getSpec().getBacking() != null
								&& hostProxySwitch.getSpec().getBacking() instanceof DistributedVirtualSwitchHostMemberPnicBacking) {
							pnicBacking = (DistributedVirtualSwitchHostMemberPnicBacking) hostProxySwitch
									.getSpec().getBacking();
							if (pnicBacking != null) {
								DistributedVirtualSwitchHostMemberPnicSpec[] hostMemberPnicSpecArr = pnicBacking
										.getPnicSpec();
								if (hostMemberPnicSpecArr != null) {
									for (DistributedVirtualSwitchHostMemberPnicSpec hostMemberPnicSpec : hostMemberPnicSpecArr) {
										if (hostMemberPnicSpec != null
												&& hostMemberPnicSpec
														.getPnicDevice() != null
												&& pnicsList
														.contains(hostMemberPnicSpec
																.getPnicDevice())) {
											pnicsList.remove(hostMemberPnicSpec
													.getPnicDevice());
										}
									}
								}
							}
						}
					}
				}
			}
			if (pnicsList.size() > 0) {
				pnicIds = pnicsList.toArray(new String[pnicsList.size()]);
				System.out.println("Free pNics in " + hs.getName() + "::"
						+ pnicIds.length);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return pnicIds;
	}

	public static void main(String[] args) {
		String vcIPaddress = "10.10.10.1"; // VC ipaddress/hostname
		String userName = "Administrator@vsphere.local"; // VC username
		String passwd = "jsdhdjksjksdnuj"; // VC password
		try {
			ServiceInstance si = new ServiceInstance(new URL("https://"
					+ vcIPaddress + "/sdk"), userName, passwd, true);
			AddHostsintoDVSwitch obj = new AddHostsintoDVSwitch();
			List<String> hostList = new ArrayList<String>();
			hostList.add("10.10.10.2");
			hostList.add("10.10.10.3");
			obj.addHostsintoDVS(si, "DSwitch", hostList);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
