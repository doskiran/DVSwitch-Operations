package vmMigration;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import com.vmware.vim25.Description;
import com.vmware.vim25.DistributedVirtualSwitchPortConnection;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ServiceLocator;
import com.vmware.vim25.ServiceLocatorCredential;
import com.vmware.vim25.ServiceLocatorNamePassword;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VirtualPCNet32;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

public class CrossVC_DVSvMotion {

	public boolean relocateVM(ServiceInstance srcSI, String vmName,
			String destVCIP, String destUsername, String destPassword,
			String destHost, String dvSwitchName, String dvPortgroupName) {
		String portGroupKey = null;
		String dvsUUID = null;
		try {
			VirtualMachine vm = (VirtualMachine) new InventoryNavigator(
					srcSI.getRootFolder()).searchManagedEntity(
					"VirtualMachine", vmName);
			if (vm == null) {
				System.out.println("No VM " + vmName + " found");
				srcSI.getServerConnection().logout();
				return false;
			}
			ServiceInstance destSI = new ServiceInstance(new URL("https://"
					+ destVCIP + "/sdk"), destUsername, destPassword, true);
			Folder destRootFolder = destSI.getRootFolder();
			HostSystem hs = (HostSystem) new InventoryNavigator(destRootFolder)
					.searchManagedEntity("HostSystem", destHost);
			ComputeResource cr = (ComputeResource) hs.getParent();
			DistributedVirtualSwitch dvs = (DistributedVirtualSwitch) new InventoryNavigator(
					destSI.getRootFolder()).searchManagedEntity(
					"DistributedVirtualSwitch", dvSwitchName);
			DistributedVirtualPortgroup[] dvPortgroup = dvs.getPortgroup();
			for (int j = 0; j < dvPortgroup.length; j++) {
				String portGroupName = dvPortgroup[j].getName();
				if (portGroupName.equalsIgnoreCase(dvPortgroupName)) {
					portGroupKey = dvPortgroup[j].getKey();
				}
			}
			dvsUUID = dvs.getUuid();

			ManagedObjectReference dsMor = null;
			Datastore[] dsArr = hs.getDatastores();
			for (Datastore ds : dsArr) {
				if (ds.getName().equalsIgnoreCase("sharedVmfs-0")) {
					dsMor = ds.getMOR();
					break;
				}
			}

			// Below is code for ServiceLocator which is key for this vMotion
			// to happen
			ServiceLocator serviceLoc = new ServiceLocator();
			ServiceLocatorCredential credential = new ServiceLocatorNamePassword();
			((ServiceLocatorNamePassword) credential).setUsername(destUsername);
			((ServiceLocatorNamePassword) credential).setPassword(destPassword);
			serviceLoc.setCredential(credential);

			String instanceUuid = destSI.getAboutInfo().getInstanceUuid();
			serviceLoc.setInstanceUuid(instanceUuid);
			//If ThumpPrint is null, set manually.
			serviceLoc.setSslThumbprint(getVCThumpPrint(destSI));
			serviceLoc.setUrl(updateUrlWithDnsName(destSI.getServerConnection()
					.getUrl().toString()));
			VirtualMachineRelocateSpec vmRelSpec = new VirtualMachineRelocateSpec();
			vmRelSpec.setFolder(getHostDataCenter(destSI, hs).getVmFolder()
					.getMOR());
			vmRelSpec.setHost(hs.getMOR());
			vmRelSpec.setDatastore(dsMor);
			vmRelSpec.setDeviceChange(getVirtualDeviceConfigSpec(vm, dvsUUID,
					portGroupKey, "Network adapter 1"));
			vmRelSpec.setPool((cr != null) ? cr.getResourcePool().getMOR() : hs
					.getMOR());
			vmRelSpec.setService(serviceLoc);
			Task task = vm.relocateVM_Task(vmRelSpec);
			TaskInfo ti = waitFor(task);
			if (ti.getState() == TaskInfoState.error) {
				System.out.println("VM migration failed" + vmName);
				return false;
			}
			System.out.println("VM migration  successfully" + vmName);
			srcSI.getServerConnection().logout();
			destSI.getServerConnection().logout();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	public Datacenter getHostDataCenter(ServiceInstance si, HostSystem host)
			throws Exception {
		Datacenter destDC = null;
		String dataCenterName = "";
		ManagedEntity hme = host.getParent();
		ManagedObjectReference mob = hme.getMOR();
		String enityType = mob.getType();
		while (!enityType.equals("Datacenter")) {
			hme = hme.getParent();
			mob = hme.getMOR();
			enityType = mob.getType();
		}
		dataCenterName = hme.getName();
		destDC = (Datacenter) new InventoryNavigator(si.getRootFolder())
				.searchManagedEntity("Datacenter", dataCenterName);
		return destDC;
	}

	public String getVCThumpPrint(ServiceInstance si) {
		String destVCThumpPrint = null;
		try {
			destVCThumpPrint = si.getSessionManager().acquireCloneTicket()
					.split("tp-")[1];
		} catch (Exception e) {
			System.out.println("Error::" + e.getMessage());
		}
		System.out.println("Dest VC ThumpPrint::" + destVCThumpPrint);
		return destVCThumpPrint;
	}

	public static String updateUrlWithDnsName(String endPointUrl)
			throws UnknownHostException, MalformedURLException {
		InetAddress hostAddress = InetAddress.getByName(new URL(endPointUrl)
				.getHost());
		String hostIpAddress = hostAddress.getHostAddress();
		String hostDnsName = hostAddress.getHostName();
		if (endPointUrl.contains(hostIpAddress)) {
			endPointUrl = endPointUrl.replaceAll(hostIpAddress, hostDnsName)
					.replace("[", "").replace("]", "");
			System.out.println("Updated URL name :" + endPointUrl);
		}
		return endPointUrl;
	}

	public static VirtualDeviceConfigSpec[] getVirtualDeviceConfigSpec(
			VirtualMachine vm, String dvsUUID, String portGroupKey,
			String networkAdapter) {
		try {
			VirtualDevice[] vdeviceArray = (VirtualDevice[]) vm
					.getPropertyByPath("config.hardware.device");
			VirtualEthernetCardDistributedVirtualPortBackingInfo nicBacking = new VirtualEthernetCardDistributedVirtualPortBackingInfo();
			List<VirtualDeviceConfigSpec> nicSpecAL = new ArrayList<VirtualDeviceConfigSpec>();
			for (int k = 0; k < vdeviceArray.length; k++) {
				Description vDetails = vdeviceArray[k].getDeviceInfo();
				if (vDetails.getLabel().equalsIgnoreCase(networkAdapter)) {
					VirtualDeviceConfigSpec nicSpec = new VirtualDeviceConfigSpec();
					nicSpec.setOperation(VirtualDeviceConfigSpecOperation.edit);
					VirtualEthernetCard nic = new VirtualPCNet32();
					nic = (VirtualEthernetCard) vdeviceArray[k];
					DistributedVirtualSwitchPortConnection portConn = new DistributedVirtualSwitchPortConnection();
					portConn.setPortgroupKey(portGroupKey);
					portConn.setSwitchUuid(dvsUUID);
					nicBacking.setPort(portConn);
					nic.setBacking(nicBacking);
					// nic.setAddressType("Generated");
					Description desc = new Description();
					desc.setLabel(portGroupKey);
					desc.setSummary(portGroupKey);
					nic.setDeviceInfo(desc);
					nicSpec.setDevice(nic);
					nicSpecAL.add(nicSpec);
					break;
				}
			}
			VirtualDeviceConfigSpec[] nicSpecArray = nicSpecAL
					.toArray(new VirtualDeviceConfigSpec[nicSpecAL.size()]);
			return nicSpecArray;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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
		String vmName = "VM1"; // SourceVC VM name
		String srcVCIP = "10.10.10.11"; // SourceVC IP/hostname
		String destVCIP = "10.10.10.12"; // DestinationVC IP/hostname
		String userName = "Administrator@vsphere.local";
		String passwd = "Afdgfg%3we1";
		String destHost = "10.20.20.10"; // DestinationVC ESXi hostname to migrate VM
		String dvSwitchName = "DSwitch"; // DestinationVC dvSwitch name
		String dvPortgroupName = "DPortGroup";
		try {
			ServiceInstance srcSI = new ServiceInstance(new URL("https://"
					+ srcVCIP + "/sdk"), userName, passwd, true);
			CrossVC_DVSvMotion vMotion = new CrossVC_DVSvMotion();
			vMotion.relocateVM(srcSI, vmName, destVCIP, userName, passwd,
					destHost, dvSwitchName, dvPortgroupName);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
