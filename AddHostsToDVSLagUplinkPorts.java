package dvSwitchOperations;

//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Add Hosts pnics to the LAG uplink ports in DVSwitch.
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import com.vmware.vim25.ConfigSpecOperation;
import com.vmware.vim25.DVSConfigSpec;
import com.vmware.vim25.DistributedVirtualSwitchHostMemberConfigSpec;
import com.vmware.vim25.DistributedVirtualSwitchHostMemberPnicBacking;
import com.vmware.vim25.DistributedVirtualSwitchHostMemberPnicSpec;
import com.vmware.vim25.HostNetworkInfo;
import com.vmware.vim25.HostProxySwitch;
import com.vmware.vim25.HostVirtualSwitch;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.PhysicalNic;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VMwareDVSConfigInfo;
import com.vmware.vim25.VMwareDvsLacpGroupConfig;
import com.vmware.vim25.VMwareDvsLacpGroupSpec;
import com.vmware.vim25.VMwareDvsLacpLoadBalanceAlgorithm;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.HostNetworkSystem;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VmwareDistributedVirtualSwitch;

public class AddHostsToDVSLagUplinkPorts {

	/**
	 * This method is to add hosts free pnis to the LAG uplink ports
	 * 
	 * @param si
	 * @param dvsName
	 * @param hostNames
	 * @return
	 * @throws Exception
	 */
	public boolean addHostsToDVSLagUplinkPorts(ServiceInstance si,
			String dvsName, List<String> hostNames) throws Exception {
		System.out
				.println("Enter into addHostsToDVSLagUplinkPorts::" + dvsName);
		DistributedVirtualSwitch dvSwitch = null;
		boolean status = false;
		List<DistributedVirtualSwitchHostMemberConfigSpec> hostMemberConfigSpecList = new ArrayList<DistributedVirtualSwitchHostMemberConfigSpec>();
		try {
			if (si != null) {
				dvSwitch = (DistributedVirtualSwitch) new InventoryNavigator(
						si.getRootFolder()).searchManagedEntity(
						"DistributedVirtualSwitch", dvsName);
				if (dvSwitch != null) {
					if (addHostToDVS(si, dvSwitch, hostNames)) {
						DVSConfigSpec dvsConfigSpec = new DVSConfigSpec();
						for (String hostName : hostNames) {
							HostSystem hs = (HostSystem) new InventoryNavigator(
									si.getRootFolder()).searchManagedEntity(
									"HostSystem", hostName);
							// Get the free physical nics from the host
							String[] freePnics = getFreePnics(hs);
							if ((freePnics != null) && (freePnics.length > 0)) {
								if ((freePnics != null)
										&& (freePnics.length > 0)) {
									DistributedVirtualSwitchHostMemberConfigSpec hostMemberconfigSpec = new DistributedVirtualSwitchHostMemberConfigSpec();
									DistributedVirtualSwitchHostMemberPnicBacking pnicBacking = new DistributedVirtualSwitchHostMemberPnicBacking();
									DistributedVirtualSwitchHostMemberPnicSpec pnicSpec = new DistributedVirtualSwitchHostMemberPnicSpec();
									List<DistributedVirtualSwitchHostMemberPnicSpec> pnicSpecList = new ArrayList<DistributedVirtualSwitchHostMemberPnicSpec>();
									hostMemberconfigSpec.setHost(hs.getMOR());
									hostMemberconfigSpec
											.setOperation(ConfigSpecOperation.edit
													.toString());
									// assigning first host free pnic to lag
									// port
									pnicSpec.setPnicDevice(freePnics[0]);
									VMwareDVSConfigInfo vDvsConfig = (VMwareDVSConfigInfo) dvSwitch
											.getConfig();
									VMwareDvsLacpGroupConfig[] lacpGrpList = vDvsConfig
											.getLacpGroupConfig();
									// assigning the Lag first uplink port key
									pnicSpec.setUplinkPortKey(lacpGrpList[0]
											.getUplinkPortKey()[0]);

									pnicSpecList.add(pnicSpec);
									DistributedVirtualSwitchHostMemberPnicSpec[] pnicSpecArr = pnicSpecList
											.toArray(new DistributedVirtualSwitchHostMemberPnicSpec[pnicSpecList
													.size()]);
									pnicBacking.setPnicSpec(pnicSpecArr);
									hostMemberconfigSpec
											.setBacking(pnicBacking);
									hostMemberConfigSpecList
											.add(hostMemberconfigSpec);
								}
							}
						}
						if (!hostMemberConfigSpecList.isEmpty()) {
							dvsConfigSpec.setConfigVersion(dvSwitch.getConfig()
									.getConfigVersion());

							DistributedVirtualSwitchHostMemberConfigSpec[] hostMemberConfigSpecArr = hostMemberConfigSpecList
									.toArray(new DistributedVirtualSwitchHostMemberConfigSpec[hostMemberConfigSpecList
											.size()]);
							dvsConfigSpec.setHost(hostMemberConfigSpecArr);
							Task task = dvSwitch
									.reconfigureDvs_Task(dvsConfigSpec);
							TaskInfo ti = waitFor(task);
							if (ti.getState() == TaskInfoState.error) {
								System.out
										.println("Unable to add ESXi servers::"
												+ ti.getError()
														.getLocalizedMessage());
								return false;
							}
							System.out
									.println("Successfully added ESXi servers free pnic to LAG uplink ports ::"
											+ dvSwitch.getName());
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("addHostsToDVSLagUplinkPorts Error::"
					+ e.getMessage());
		}
		return status;
	}

	/**
	 * This method will add the hosts into dvSwitch without assigning any pnics
	 * to uplinks., because to get the LAG uplink port keys
	 * 
	 * @param si
	 * @param dvSwitch
	 * @param hostNames
	 * @return
	 */
	public boolean addHostToDVS(ServiceInstance si,
			DistributedVirtualSwitch dvSwitch, List<String> hostNames) {
		try {
			DVSConfigSpec dvsConfigSpec = new DVSConfigSpec();
			List<DistributedVirtualSwitchHostMemberConfigSpec> hostMemberConfigSpecList = new ArrayList<DistributedVirtualSwitchHostMemberConfigSpec>();
			for (String hostName : hostNames) {
				HostSystem hs = (HostSystem) new InventoryNavigator(
						si.getRootFolder()).searchManagedEntity("HostSystem",
						hostName);
				if (hs != null) {
					if (!isHostInDVS(dvSwitch, hs.getMOR())) {
						DistributedVirtualSwitchHostMemberConfigSpec hostMemberconfigSpec = new DistributedVirtualSwitchHostMemberConfigSpec();
						DistributedVirtualSwitchHostMemberPnicBacking pnicBacking = new DistributedVirtualSwitchHostMemberPnicBacking();
						List<DistributedVirtualSwitchHostMemberPnicSpec> pnicSpecList = new ArrayList<DistributedVirtualSwitchHostMemberPnicSpec>();
						hostMemberconfigSpec.setHost(hs.getMOR());
						hostMemberconfigSpec
								.setOperation(ConfigSpecOperation.add
										.toString());
						DistributedVirtualSwitchHostMemberPnicSpec[] pnicSpecArr = pnicSpecList
								.toArray(new DistributedVirtualSwitchHostMemberPnicSpec[pnicSpecList
										.size()]);
						pnicBacking.setPnicSpec(pnicSpecArr);
						hostMemberconfigSpec.setBacking(pnicBacking);
						hostMemberConfigSpecList.add(hostMemberconfigSpec);
					}
				}
			}
			if (!hostMemberConfigSpecList.isEmpty()) {
				dvsConfigSpec.setConfigVersion(dvSwitch.getConfig()
						.getConfigVersion());
				DistributedVirtualSwitchHostMemberConfigSpec[] hostMemberConfigSpecArr = hostMemberConfigSpecList
						.toArray(new DistributedVirtualSwitchHostMemberConfigSpec[hostMemberConfigSpecList
								.size()]);
				dvsConfigSpec.setHost(hostMemberConfigSpecArr);
				Task task = dvSwitch.reconfigureDvs_Task(dvsConfigSpec);
				TaskInfo ti = waitFor(task);
				if (ti.getState() == TaskInfoState.error) {
					System.out.println("Unable to add ESXi servers::"
							+ ti.getError().getLocalizedMessage());
					return false;
				}
				System.out.println("Successfully added ESXi servers in DVS ::"
						+ dvSwitch.getName());
			}
		} catch (Exception e) {
			System.out.println("addHostToDVS Error::" + e.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * This method returns true, if host exist in dvSwitch
	 * 
	 * @param dvs
	 * @param hostMor
	 * @return
	 */
	public boolean isHostInDVS(DistributedVirtualSwitch dvs,
			ManagedObjectReference hostMor) {
		try {
			ManagedObjectReference[] hsMorArr = dvs.getSummary()
					.getHostMember();
			for (ManagedObjectReference hsMor : hsMorArr) {
				if (hsMor.equals(hostMor)) {
					return true;
				}
			}
		} catch (Exception e) {
			return false;
		}
		return false;
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
					lacpGroupConf.setUplinkNum(2);
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
							.println("Successfully Lag group created in DVS:: LAG1");
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
		String passwd = "safsdvdfbvdfbb"; // VC password
		String dvSwitchName = "DSwitch"; // DVSwitch name
		try {
			ServiceInstance si = new ServiceInstance(new URL("https://"
					+ vcIPaddress + "/sdk"), userName, passwd, true);
			List<String> hostList = new ArrayList<String>();
			hostList.add("10.10.10.40");
			hostList.add("10.10.10.50");
			AddHostsToDVSLagUplinkPorts obj = new AddHostsToDVSLagUplinkPorts();
			obj.createLag(si, dvSwitchName);
			obj.addHostsToDVSLagUplinkPorts(si, dvSwitchName, hostList);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
