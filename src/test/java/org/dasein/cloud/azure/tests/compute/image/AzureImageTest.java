package org.dasein.cloud.azure.tests.compute.image;

import static org.dasein.cloud.azure.tests.HttpMethodAsserts.*;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.azure.compute.AzureComputeServices;
import org.dasein.cloud.azure.compute.image.AzureMachineImage;
import org.dasein.cloud.azure.compute.image.AzureOSImage;
import org.dasein.cloud.azure.compute.vm.AzureVM;
import org.dasein.cloud.azure.tests.AzureTestsBase;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageCreateOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VmState;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import mockit.*;

public class AzureImageTest extends AzureTestsBase {
	
	protected final String VM_ID = "TESTVMID";
	protected final String IMAGE_ID = "TESTIMAGEID";
	protected final String IMAGE_NAME = "TESTIMAGE";
	
	protected final String CAPTURE_IMAGE_URL = "%s/%s/services/hostedservices/%s/deployments/%s/roleInstances/%s/Operations";
	protected final String REMOVE_VM_IMAGE_URL = "%s/%s/services/vmimages/%s?comp=media";
	protected final String REMOVE_OS_IMAGE_URL = "%s/%s/services/images/%s?comp=media";
	
	@Mocked
	protected AzureComputeServices azureComputeServicesMock;
	@Mocked
	protected AzureVM azureVirtualMachineSupportMock;
	@Mocked
	protected VirtualMachine virtualMachineMock;
	
	@Rule
    public final TestName name = new TestName();
	
	@Before
	public void initExpectations() throws InternalException, CloudException {
        
		String methodName = name.getMethodName().substring(4, name.getMethodName().length()).toLowerCase();
        if (methodName.startsWith("capture")) {
    		new NonStrictExpectations() { 
    			{ azureMock.getComputeServices(); result = azureComputeServicesMock; }
    			{ azureMock.hold(); }
    			{ azureComputeServicesMock.getVirtualMachineSupport(); result = azureVirtualMachineSupportMock; }
            };
            
            final AzureOSImage anyInstance = new AzureOSImage(azureMock);
	        new NonStrictExpectations(AzureOSImage.class) {
	        	{ anyInstance.getImage(anyString); result = MachineImage.getInstance(ACCOUNT_NO, REGION, IMAGE_ID, 
	        			ImageClass.MACHINE, MachineImageState.PENDING, IMAGE_NAME, IMAGE_NAME, Architecture.I64, Platform.RHEL); }
	        };
	        new NonStrictExpectations() {
	        	{ azureVirtualMachineSupportMock.getVirtualMachine(anyString); result = virtualMachineMock; }
	        	{ virtualMachineMock.getProviderVirtualMachineId(); result = VM_ID; }
	        	{ virtualMachineMock.getPlatform(); result = Platform.RHEL; }
	        	{ virtualMachineMock.getCurrentState(); result = VmState.STOPPED; }
	        	{ virtualMachineMock.getTag("serviceName"); result = SERVICE_NAME; }
	        	{ virtualMachineMock.getTag("deploymentName"); result = DEPLOYMENT_NAME; }
	        	{ virtualMachineMock.getTag("roleName"); result = ROLE_NAME; }
	        };
	        if (!methodName.endsWith("retrieveimagetimeout")) {
		        new NonStrictExpectations(AzureOSImage.class) {
		        	{ anyInstance.getImage(anyString); result = MachineImage.getInstance(ACCOUNT_NO, REGION, IMAGE_ID, 
		        			ImageClass.MACHINE, MachineImageState.PENDING, IMAGE_NAME, IMAGE_NAME, Architecture.I64, Platform.RHEL); }
		        };
	        } else {
		        new NonStrictExpectations(AzureOSImage.class) {
		        	{ anyInstance.getImage(anyString); result = null; }
		        };
	        }
	        if (methodName.endsWith("terminateservicefailed")) {
	        	new Expectations() {
	    			{	azureVirtualMachineSupportMock.terminateService(anyString, anyString);
	    				result = new CloudException("Terminate service failed!"); }
	    		};
	        }
        } else if (methodName.startsWith("remove")) {
        	
        	final AzureOSImage anyInstance = new AzureOSImage(azureMock);
    		
    		if (methodName.contains("null")) {
    			new NonStrictExpectations(AzureOSImage.class) {
		        	{ anyInstance.getImage(anyString); result = null; }
		        };
    		} else {
    			final AzureMachineImage azureMachineImage = new AzureMachineImage();
        		azureMachineImage.setAzureImageType("osimage");
        		azureMachineImage.setProviderMachineImageId(IMAGE_ID);
	        	if (methodName.contains("os")) {
	        		azureMachineImage.setAzureImageType("osimage");
	        	} else if (methodName.contains("vm")) {
	        		azureMachineImage.setAzureImageType("vmimage");
	        	}
	        	new NonStrictExpectations(AzureOSImage.class) {
		        	{ anyInstance.getImage(anyString); result = azureMachineImage; }
		        };
    		}
        } 
	}
	
	@Before
	public void initMockUps() {
		
		final String methodName = name.getMethodName().substring(4, name.getMethodName().length()).toLowerCase();
		
		final CloseableHttpResponse responseMock = getHttpResponseMock(
				getStatusLineMock(HttpServletResponse.SC_OK), 
				null, 
				new Header[]{});
		
		if (methodName.startsWith("capture")) {
			new MockUp<CloseableHttpClient>() {
	            @Mock(invocations = 1)
	            public CloseableHttpResponse execute(HttpUriRequest request) {
	        		assertPost(request, 
	        				String.format(CAPTURE_IMAGE_URL, ENDPOINT, ACCOUNT_NO, SERVICE_NAME, DEPLOYMENT_NAME, ROLE_NAME));
	            	return responseMock;
	            }
	        };
		} else if (methodName.startsWith("remove") && !methodName.contains("null")) {
			if (methodName.contains("os")) {
				new MockUp<CloseableHttpClient>() {
		            @Mock(invocations = 1)
		            public CloseableHttpResponse execute(HttpUriRequest request) {
		        		assertDelete(request, String.format(REMOVE_OS_IMAGE_URL, ENDPOINT, ACCOUNT_NO, IMAGE_ID));
		            	return responseMock;
		            }
		        };
			} else {
				new MockUp<CloseableHttpClient>() {
		            @Mock(invocations = 1)
		            public CloseableHttpResponse execute(HttpUriRequest request) {
		        		assertDelete(request, String.format(REMOVE_VM_IMAGE_URL, ENDPOINT, ACCOUNT_NO, IMAGE_ID));
		            	return responseMock;
		            }
		        };
			}
		} 
//		else if (methodName.startsWith("get") || methodName.startsWith("list")) {
//			OSImageModel globalOSImageModel = new OSImageModel();
//			OSImageModel privateOSImageModel = new OSImageModel();
//			OSImageModel publicOSImageModel = new OSImageModel();
//			//TODO init
//        	OSImagesModel globalOSImagesModel = new OSImagesModel();
//        	globalOSImagesModel.setImages(Arrays.asList(globalOSImageModel, privateOSImageModel));
//        	OSImagesModel privateOSImagesModel = new OSImagesModel();
//        	privateOSImagesModel.setImages(Arrays.asList(privateOSImageModel));
//        	OSImagesModel bothPrivatePublicOSImagesModel = new OSImagesModel();
//        	bothPrivatePublicOSImagesModel.setImages(Arrays.asList(privateOSImageModel, publicOSImageModel));
//        	
//        	VMImageModel globalVMImageModel = new VMImageModel();
//			VMImageModel privateVMImageModel = new VMImageModel();
//			VMImageModel publicVMImageModel = new VMImageModel();
//			//TODO init
//        	VMImagesModel globalVMImagesModel = new VMImagesModel();
//        	globalVMImagesModel.setVmImages(Arrays.asList(globalVMImageModel, privateVMImageModel));
//        	VMImagesModel privateVMImagesModel = new VMImagesModel();
//        	privateVMImagesModel.setVmImages(Arrays.asList(privateVMImageModel));
//        	VMImagesModel bothPrivatePublicVMImagesModel = new VMImagesModel();
//        	bothPrivatePublicVMImagesModel.setVmImages(Arrays.asList(privateVMImageModel, publicVMImageModel));
//        }
	}
	
	@Test
	public void testCaptureWithOption() throws CloudException, InternalException {
        
        final AzureOSImage support = new AzureOSImage(azureMock);
     
        ImageCreateOptions options = ImageCreateOptions.getInstance(virtualMachineMock, IMAGE_NAME, IMAGE_NAME);
        MachineImage image = support.captureImage(options);
        
        assertNotNull("Capture image returns null image", image);
        assertEquals("Capture image returns invalid image id", IMAGE_ID, image.getProviderMachineImageId());
	}
	
	@Ignore //TODO: getProvider().hold() always failed, mock?
	@Test
	public void testCaptureWithTask() throws CloudException, InternalException, InterruptedException {
		
		final AzureOSImage support = new AzureOSImage(azureMock);
		final AtomicBoolean taskRun = new AtomicBoolean(false);
		
		AsynchronousTask<MachineImage> task = new AsynchronousTask<MachineImage>() {
			@Override
			public synchronized void completeWithResult(@Nullable MachineImage result) {
				super.completeWithResult(result);
				assertNotNull("Capture image returns null image", result);
				assertEquals("Capture image returns invalid image id", IMAGE_ID, result.getProviderMachineImageId());
				taskRun.compareAndSet(false, true);
			}
		};
		
		ImageCreateOptions options = ImageCreateOptions.getInstance(virtualMachineMock, IMAGE_NAME, IMAGE_NAME);
        support.captureImageAsync(options, task);
		while(!task.isComplete()) {
			Thread.sleep(1000);
		}
        assertTrue("Capture with task doesn't have a task run", taskRun.get());
	}
	
	@Ignore //TODO: pass, but time-cost
	@Test(expected = CloudException.class)
	public void testCaptureRetrieveImageTimeout() throws CloudException, InternalException {
		
		AzureOSImage support = new AzureOSImage(azureMock);
		ImageCreateOptions options = ImageCreateOptions.getInstance(virtualMachineMock, IMAGE_NAME, IMAGE_NAME);
		support.captureImage(options);
	}
	
	@Test(expected = CloudException.class)
	public void testCaptureTerminateServiceFailed() throws InternalException, CloudException {
		
		AzureOSImage support = new AzureOSImage(azureMock);
		ImageCreateOptions options = ImageCreateOptions.getInstance(virtualMachineMock, IMAGE_NAME, IMAGE_NAME);
		support.captureImage(options);
	}
	
	@Test
	public void testRemoveVMImage() throws CloudException, InternalException {
		AzureOSImage support = new AzureOSImage(azureMock);
		support.remove(IMAGE_ID);
	}
	
	@Test
	public void testRemoveOSImage() throws CloudException, InternalException {
		AzureOSImage support = new AzureOSImage(azureMock);
		support.remove(IMAGE_ID);
	}
	
	@Test(expected = CloudException.class)
	public void testRemoveNullImage() throws CloudException, InternalException {
		AzureOSImage support = new AzureOSImage(azureMock);
		support.remove(IMAGE_ID);
	}
	
//	@Test
//	public void testGetImage() {
//		
//	}
	
}
