package dvSwitchOperations;

//:: # Author: P Kiran Kumar
//:: # Product/Feature: VC
//:: # Description: Adds the VMkernel network adapter for all the hosts in the dvSwitch and assign them to dvPortgroup ports and also enable vMotion service in vmkernel nics using vSphere java API. 
import java.net.URL;

import com.vmware.vim25.DistributedVirtualSwitchPortConnection;
import com.vmware.vim25.HostIpConfig;
import com.vmware.vim25.HostVirtualNicManagerNicType;
import com.vmware.vim25.HostVirtualNicSpec;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.HostNetworkSystem;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.HostVirtualNicManager;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;

public class AddVMkernelToHostsInDVS {

	/**
	 * This method is used to add the VMkernel network adapters for all the
	 * hosts in the dvSwitch and assign them to distributed portgroup ports.
	 * Also enable vMotion service in vmkernel nic.
	 * 
	 * @param si
	 * @param dvSwitchName
	 * @param dvPortGroupName
	 * @return
	 */
	public void addVMKernelAndEnableVMotionForDVSHosts(ServiceInstance si,
			String dvSwitchName, String dvPortGroupName) {
		DistributedVirtualSwitch dvSwitch = null;
		DistributedVirtualPortgroup dvPortgroup = null;
		try {
			if (si != null) {
				dvSwitch = (DistributedVirtualSwitch) new InventoryNavigator(
						si.getRootFolder()).searchManagedEntity(
						"DistributedVirtualSwitch", dvSwitchName);
				if (dvSwitch != null) {
					dvPortgroup = getDistributedVirtualPortgroupObj(dvSwitch,
							dvPortGroupName);
					if (dvPortgroup != null) {
						ManagedObjectReference[] hostMors = dvSwitch
								.getSummary().getHostMember();
						if (hostMors != null && hostMors.length > 0) {
							for (ManagedObjectReference hostMor : hostMors) {
								DistributedVirtualSwitchPortConnection portConnection = getDVSPortConnection(
										dvSwitch.getUuid(),
										dvPortgroup.getKey());
								addVMKernelAndEnableVMotion(si, hostMor,
										portConnection, dvPortGroupName);
							}
						} else {
							System.out
									.println("Failed to find hosts in the dvSwitch - "
											+ dvSwitchName);
						}
					} else {
						System.out.println(" Failed to find dvPortgroup - "
								+ dvPortGroupName + " in the dvSwitch - "
								+ dvSwitchName);
					}
				} else {
					System.out.println("Failed to find a dvSwitch - "
							+ dvSwitchName);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Adds a virtual host/VMkernel network adapter.
	 * 
	 * @param si
	 * @param hostMor
	 * @param portConnection
	 * @param portgroup
	 * @return
	 */
	public void addVMKernelAndEnableVMotion(ServiceInstance si,
			ManagedObjectReference hostMor,
			DistributedVirtualSwitchPortConnection portConnection,
			String portgroup) {
		String device = null;
		HostVirtualNicSpec hostVnicSpec = null;
		HostSystem hs = null;
		HostNetworkSystem hsNetSys = null;
		HostVirtualNicManager hsVnicMgr = null;
		try {
			hostVnicSpec = getHostvNicSpec(portConnection);
			hs = new HostSystem(si.getServerConnection(), hostMor);
			hsNetSys = hs.getHostNetworkSystem();
			device = hsNetSys.addVirtualNic("", hostVnicSpec);
			if (device != null) {
				hsVnicMgr = hs.getHostVirtualNicManager();
				hsVnicMgr
						.selectVnicForNicType(
								HostVirtualNicManagerNicType.vmotion.toString(),
								device);
				System.out
						.println("Successfully added vmkernel nic and enabled vmotion on the nic - "
								+ device + " for the host - " + hs.getName());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public DistributedVirtualPortgroup getDistributedVirtualPortgroupObj(
			DistributedVirtualSwitch dvSwitch, String dvPortGroupName) {
		for (DistributedVirtualPortgroup dvpg : dvSwitch.getPortgroup()) {
			if (dvpg.getName().equals(dvPortGroupName)) {
				return dvpg;
			}
		}
		return null;
	}

	/**
	 * Building DistributedVirtualSwitchPortConnection object
	 * 
	 * @param switchUuid
	 * @param portKey
	 * @param portgroupKey
	 * @return
	 */
	public DistributedVirtualSwitchPortConnection getDVSPortConnection(
			String switchUuid, String portgroupKey) {
		DistributedVirtualSwitchPortConnection connection = new DistributedVirtualSwitchPortConnection();
		connection.setSwitchUuid(switchUuid);
		connection.setPortKey(null);
		connection.setPortgroupKey(portgroupKey);
		return connection;
	}

	/**
	 * Building HostVirtualNicSpec object
	 * 
	 * @param portConnection
	 * @return
	 */
	public HostVirtualNicSpec getHostvNicSpec(
			DistributedVirtualSwitchPortConnection portConnection) {
		HostVirtualNicSpec hsvNicspec = new HostVirtualNicSpec();
		hsvNicspec.setDistributedVirtualPort(portConnection);
		HostIpConfig ip = new HostIpConfig();
		ip.setDhcp(true);
		ip.setIpAddress(null);
		ip.setSubnetMask(null);
		hsvNicspec.setIp(ip);
		return hsvNicspec;
	}

	public static void main(String[] args) {
		String vcIPaddress = "10.10.10.30"; // VC ipaddress/hostname
		String userName = "Administrator@vsphere.local"; // VC username
		String passwd = "vdfverfddsac"; // VC password
		String dvSwitchName = "testDvs1"; // DVSwitch name
		String dvPortGroupName = "testDVPortgroup1"; // DVPortgroup name
		try {
			ServiceInstance si = new ServiceInstance(new URL("https://"
					+ vcIPaddress + "/sdk"), userName, passwd, true);
			AddVMkernelToHostsInDVS obj = new AddVMkernelToHostsInDVS();
			obj.addVMKernelAndEnableVMotionForDVSHosts(si, dvSwitchName,
					dvPortGroupName);
			si.getServerConnection().logout();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
