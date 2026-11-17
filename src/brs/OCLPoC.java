
package brs;

import brs.props.PropertyService;
import brs.props.Props;
import brs.services.BlockService;
import brs.util.MiningPlot;
import org.jocl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;

import static org.jocl.CL.*;

final class OCLPoC {
    private OCLPoC() {
    }

    private static final Logger logger = LoggerFactory.getLogger(OCLPoC.class);
    private static int HASHES_PER_ENQUEUE_DYNAMIC;
    private static int HASHES_PER_ENQUEUE;
    private static int MEM_PERCENT;

    private static cl_context ctx;
    private static cl_command_queue queue;
    private static cl_program program;
    private static cl_kernel genKernel;
    private static cl_kernel getKernel;
    private static cl_kernel getKernel2;

    private static long maxItems;
    private static long MAX_GROUP_ITEMS;

    private static final Object oclLock = new Object();

    // guarded by oclLock: OpenCL is set up on first use, once; a failure is kept and rethrown
    private static boolean initAttempted;
    private static OCLCheckerException initFailure;

    private static final long BUFFER_PER_ITEM = (long) MiningPlot.PLOT_SIZE + 16;
    private static final long MEM_PER_ITEM = 8 // id
            + 8 // nonce
            + BUFFER_PER_ITEM // buffer
            + 4 // scoop num
            + MiningPlot.SCOOP_SIZE; // output scoop

    /**
     * Sets up OpenCL on first use. Never runs from {@link #destroy()}, so a node that never used the
     * GPU does not initialize it at shutdown. A failed setup is not retried, and neither is setup after
     * {@link #destroy()}.
     */
    private static void ensureInitialized() {
        synchronized (oclLock) {
            if (!initAttempted) {
                initAttempted = true;
                try {
                    initialize();
                } catch (OCLCheckerException e) {
                    initFailure = e;
                } catch (RuntimeException | LinkageError e) {
                    // LinkageError: the native OpenCL library is missing
                    initFailure = new OCLCheckerException("OpenCL initialization failed", e);
                }
                if (initFailure != null) {
                    logger.warn("GPU acceleration is not available, PoC verification uses the CPU."
                            + " Set GPU.Acceleration=false for full CPU verification throughput", initFailure);
                }
            }
            if (initFailure != null) {
                throw initFailure;
            }
        }
    }

    private static void initialize() {
        PropertyService propertyService = Signum.getPropertyService();
        HASHES_PER_ENQUEUE = propertyService.getInt(Props.GPU_HASHES_PER_BATCH);
        MEM_PERCENT = propertyService.getInt(Props.GPU_MEM_PERCENT);

        try {
            boolean autoChoose = propertyService.getBoolean(Props.GPU_AUTODETECT);
            setExceptionsEnabled(true);

            int platformIndex;
            int deviceIndex;
            if (autoChoose) {
                AutoChooseResult ac = autoChooseDevice();
                if (ac == null) {
                    throw new OCLCheckerException("Autochoose failed to select a GPU");
                }
                platformIndex = ac.getPlatform();
                deviceIndex = ac.getDevice();
                logger.info("Choosing Platform {} - DeviceId: {}", platformIndex, deviceIndex);
            } else {
                platformIndex = propertyService.getInt(Props.GPU_PLATFORM_IDX);
                deviceIndex = propertyService.getInt(Props.GPU_DEVICE_IDX);
            }

            int[] numPlatforms = new int[1];
            clGetPlatformIDs(0, null, numPlatforms);

            if (numPlatforms[0] == 0) {
                throw new OCLCheckerException("No OpenCL platforms found");
            }

            if (numPlatforms[0] <= platformIndex) {
                throw new OCLCheckerException("Invalid OpenCL platform index");
            }

            cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
            clGetPlatformIDs(platforms.length, platforms, null);

            cl_platform_id platform = platforms[platformIndex];

            int[] numDevices = new int[1];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, numDevices);

            if (numDevices[0] == 0) {
                throw new OCLCheckerException("No OpenCl Devices found");
            }

            if (numDevices[0] <= deviceIndex) {
                throw new OCLCheckerException("Invalid OpenCL device index");
            }

            cl_device_id[] devices = new cl_device_id[numDevices[0]];
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, devices.length, devices, null);

            cl_device_id device = devices[deviceIndex];

            if (!checkAvailable(device)) {
                throw new OCLCheckerException("Chosen GPU must be available");
            }

            if (!checkLittleEndian(device)) {
                throw new OCLCheckerException("Chosen GPU must be little endian");
            }

            cl_context_properties ctxProps = new cl_context_properties();
            ctxProps.addProperty(CL_CONTEXT_PLATFORM, platform);

            ctx = clCreateContext(ctxProps, 1, new cl_device_id[] { device }, null, null, null);
            queue = clCreateCommandQueueWithProperties(ctx, device, new cl_queue_properties(), null);

            String source;
            try (InputStream is = OCLPoC.class.getResourceAsStream("/genscoop.cl");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                source = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            } catch (Exception e) {
                logger.error("Cannot read ocl file", e);
                throw new OCLCheckerException("Cannot read ocl file", e);
            }

            program = clCreateProgramWithSource(ctx, 1, new String[] { source }, null, null);
            clBuildProgram(program, 0, null, null, null, null);

            genKernel = clCreateKernel(program, "generate_scoops", null);
            getKernel = clCreateKernel(program, "get_scoops", null);
            getKernel2 = clCreateKernel(program, "get_scoops2", null);

            long[] genGroupSize = new long[1];
            long[] getGroupSize = new long[1];
            clGetKernelWorkGroupInfo(genKernel, device, CL_KERNEL_WORK_GROUP_SIZE, 8,
                    Pointer.to(genGroupSize), null);
            clGetKernelWorkGroupInfo(getKernel, device, CL_KERNEL_WORK_GROUP_SIZE, 8,
                    Pointer.to(getGroupSize), null);

            // Kernel-specific maximum workgroup sizes
            MAX_GROUP_ITEMS = Math.min(genGroupSize[0], getGroupSize[0]);

            if (MAX_GROUP_ITEMS <= 0) {
                throw new OCLCheckerException(
                        "OpenCL initialization error: invalid kernel workgroup size limit: "
                                + MAX_GROUP_ITEMS);
            }

            // Device capabilities
            long[] deviceMaxWorkGroupSize = new long[1];
            clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, Sizeof.size_t,
                    Pointer.to(deviceMaxWorkGroupSize), null);

            long[] localMemSize = new long[1];
            clGetDeviceInfo(device, CL_DEVICE_LOCAL_MEM_SIZE, Sizeof.cl_ulong,
                    Pointer.to(localMemSize), null);

            long[] globalMemSize = new long[1];
            clGetDeviceInfo(device, CL_DEVICE_GLOBAL_MEM_SIZE, Sizeof.cl_ulong,
                    Pointer.to(globalMemSize), null);

            // ---------------------------------------------------------------------
            // Memory-based limits
            // ---------------------------------------------------------------------

            // This kernel does not allocate explicit per-workgroup local memory,
            // therefore local memory does not further restrict the workgroup size.
            long maxItemsByLocalMem = MAX_GROUP_ITEMS;

            // Apply a safety margin to global memory to avoid allocation pressure
            long usableGlobalMem = (long) (globalMemSize[0] * 0.75);

            long maxItemsByGlobalMem = MEM_PER_ITEM > 0
                    ? usableGlobalMem / MEM_PER_ITEM
                    : Long.MAX_VALUE;

            // ---------------------------------------------------------------------
            // Hashes per enqueue (batch size)
            // ---------------------------------------------------------------------

            if (propertyService.getBoolean(Props.GPU_DYNAMIC_HASHES_PER_BATCH)) {

                HASHES_PER_ENQUEUE_DYNAMIC = (int) Math.min(
                        propertyService.getInt(Props.GPU_HASHES_PER_BATCH),
                        Math.min(MAX_GROUP_ITEMS, maxItemsByGlobalMem));

                logger.info(
                        "GPU batch size dynamically optimized to {} hashes (kernel and memory limits applied).",
                        HASHES_PER_ENQUEUE_DYNAMIC);
            } else {
                HASHES_PER_ENQUEUE_DYNAMIC = HASHES_PER_ENQUEUE;

                logger.info(
                        "GPU batch size fixed at {} hashes (dynamic optimization disabled).",
                        HASHES_PER_ENQUEUE_DYNAMIC);
            }

            // ---------------------------------------------------------------------
            // Logging of effective limits
            // ---------------------------------------------------------------------

            logger.info(
                    "Kernel-supported maximum workgroup size on this GPU: {}.",
                    MAX_GROUP_ITEMS);

            logger.info(
                    "Maximum batch size allowed by global memory constraints: {} items.",
                    maxItemsByGlobalMem);

            // ---------------------------------------------------------------------
            // Compute-unit–based throughput limit
            // ---------------------------------------------------------------------

            long computeUnits = getComputeUnits(device);
            long maxItemsByComputeUnits = computeUnits * MAX_GROUP_ITEMS;

            // Final maximum items per kernel execution (global work size)
            maxItems = Math.min(calculateMaxItemsByMem(device), maxItemsByComputeUnits);

            // Align global work size to whole workgroups
            if (maxItems % MAX_GROUP_ITEMS != 0) {
                maxItems -= (maxItems % MAX_GROUP_ITEMS);
            }

            if (maxItems <= 0) {
                throw new OCLCheckerException(
                        "OpenCL initialization error: invalid calculated maximum item count: "
                                + maxItems);
            }

            // ---------------------------------------------------------------------
            // Final summary logs
            // ---------------------------------------------------------------------

            logger.info("GPU memory usage target: {}%.", MEM_PERCENT);

            logger.info(
                    "Maximum number of items processed per GPU execution cycle: {}.",
                    maxItems);

        } catch (CLException e) {
            if (logger.isInfoEnabled()) {
                logger.info("OpenCL exception: {}", e.getMessage(), e);
            }
            destroy();
            throw new OCLCheckerException("OpenCL exception", e);
        }
    }

    /** @return the most blocks one {@link #validatePoC} call takes; 0 if OpenCL is not available */
    public static long getMaxItems() {
        try {
            ensureInitialized();
        } catch (OCLCheckerException e) {
            return 0;
        }
        return maxItems;
    }

    public static void validatePoC(HashMap<Block, Block> blocks, int pocVersion, BlockService blockService) {
        ensureInitialized();
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("starting ocl verify for: {}", blocks.size());
            }
            byte[] scoopsOut = new byte[MiningPlot.SCOOP_SIZE * blocks.size()];

            long jobSize = blocks.size();
            if (jobSize % MAX_GROUP_ITEMS != 0) {
                jobSize += (MAX_GROUP_ITEMS - (jobSize % MAX_GROUP_ITEMS));
            }

            if (jobSize > maxItems) {
                throw new IllegalStateException("Attempted to validate too many blocks at once with OCL");
            }

            long[] ids = new long[blocks.size()];
            long[] nonces = new long[blocks.size()];
            int[] scoopNums = new int[blocks.size()];

            ByteBuffer buffer = ByteBuffer.allocate(16);
            int i = 0;
            for (Block block : blocks.keySet()) {
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putLong(block.getGeneratorId());
                buffer.putLong(block.getNonce());
                buffer.flip();
                buffer.order(ByteOrder.BIG_ENDIAN);
                ids[i] = buffer.getLong();
                nonces[i] = buffer.getLong();
                buffer.clear();
                scoopNums[i] = blockService.getScoopNum(block);
                i++;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("finished preprocessing: {}", blocks.size());
            }

            synchronized (oclLock) {
                if (ctx == null) {
                    throw new OCLCheckerException("OCL context no longer exists");
                }

                cl_mem idMem = null;
                cl_mem nonceMem = null;
                cl_mem bufferMem = null;
                cl_mem scoopNumMem = null;
                cl_mem scoopOutMem = null;

                try {
                    idMem = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, 8L * blocks.size(),
                            Pointer.to(ids), null);
                    nonceMem = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                            8L * blocks.size(), Pointer.to(nonces), null);
                    bufferMem = clCreateBuffer(ctx, CL_MEM_READ_WRITE,
                            (long) (MiningPlot.PLOT_SIZE + 16) * blocks.size(), null, null);
                    scoopNumMem = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                            4L * blocks.size(), Pointer.to(scoopNums), null);
                    scoopOutMem = clCreateBuffer(ctx, CL_MEM_READ_WRITE,
                            (long) MiningPlot.SCOOP_SIZE * blocks.size(), null, null);

                    int[] totalSize = new int[] { blocks.size() };

                    clSetKernelArg(genKernel, 0, Sizeof.cl_mem, Pointer.to(idMem));
                    clSetKernelArg(genKernel, 1, Sizeof.cl_mem, Pointer.to(nonceMem));
                    clSetKernelArg(genKernel, 2, Sizeof.cl_mem, Pointer.to(bufferMem));
                    clSetKernelArg(genKernel, 5, Sizeof.cl_int, Pointer.to(totalSize));

                    int c = 0;
                    int step = HASHES_PER_ENQUEUE_DYNAMIC;
                    int[] cur = new int[1];
                    int[] st = new int[1];
                    int totalHashes = 8192;
                    while (c < totalHashes) {
                        cur[0] = c;
                        st[0] = Math.min(step, totalHashes - c);
                        clSetKernelArg(genKernel, 3, Sizeof.cl_int, Pointer.to(cur));
                        clSetKernelArg(genKernel, 4, Sizeof.cl_int, Pointer.to(st));
                        clEnqueueNDRangeKernel(queue, genKernel, 1, null, new long[] { jobSize },
                                new long[] { MAX_GROUP_ITEMS }, 0, null, null);

                        clFinish(queue);
                        c += st[0];
                    }

                    if (pocVersion == 2) {
                        clSetKernelArg(getKernel2, 0, Sizeof.cl_mem, Pointer.to(scoopNumMem));
                        clSetKernelArg(getKernel2, 1, Sizeof.cl_mem, Pointer.to(bufferMem));
                        clSetKernelArg(getKernel2, 2, Sizeof.cl_mem, Pointer.to(scoopOutMem));
                        clSetKernelArg(getKernel2, 3, Sizeof.cl_int, Pointer.to(totalSize));
                        clEnqueueNDRangeKernel(queue, getKernel2, 1, null, new long[] { jobSize },
                                new long[] { MAX_GROUP_ITEMS }, 0, null, null);
                    } else {
                        clSetKernelArg(getKernel, 0, Sizeof.cl_mem, Pointer.to(scoopNumMem));
                        clSetKernelArg(getKernel, 1, Sizeof.cl_mem, Pointer.to(bufferMem));
                        clSetKernelArg(getKernel, 2, Sizeof.cl_mem, Pointer.to(scoopOutMem));
                        clSetKernelArg(getKernel, 3, Sizeof.cl_int, Pointer.to(totalSize));
                        clEnqueueNDRangeKernel(queue, getKernel, 1, null, new long[] { jobSize },
                                new long[] { MAX_GROUP_ITEMS }, 0, null, null);
                    }

                    clEnqueueReadBuffer(queue, scoopOutMem, true, 0, (long) MiningPlot.SCOOP_SIZE * blocks.size(),
                            Pointer.to(scoopsOut), 0, null, null);
                } catch (CLException e) {
                    logger.warn("GPU exception: {}", e.getMessage(), e);
                    return;
                } catch (Exception e) {
                    logger.warn("Unexpected exception during GPU processing: {}", e.getMessage(), e);
                    return;
                } finally {
                    if (idMem != null) {
                        clReleaseMemObject(idMem);
                    }
                    if (nonceMem != null) {
                        clReleaseMemObject(nonceMem);
                    }
                    if (bufferMem != null) {
                        clReleaseMemObject(bufferMem);
                    }
                    if (scoopNumMem != null) {
                        clReleaseMemObject(scoopNumMem);
                    }
                    if (scoopOutMem != null) {
                        clReleaseMemObject(scoopOutMem);
                    }
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("finished ocl, doing rest: {}", blocks.size());
            }

            ByteBuffer scoopsBuffer = ByteBuffer.wrap(scoopsOut);
            byte[] scoop = new byte[MiningPlot.SCOOP_SIZE];

            blocks.keySet().forEach(block -> {
                try {
                    scoopsBuffer.get(scoop);
                    blockService.preVerify(block, blocks.get(block), scoop);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BlockchainProcessor.BlockNotAcceptedException e) {
                    throw new PreValidateFailException("Block failed to prevalidate", e, block);
                }
            });
            if (logger.isDebugEnabled()) {
                logger.debug("finished rest: {}", blocks.size());
            }
        } catch (CLException e) {
            // intentionally leave out of unverified cache. It won't slow it that much on
            // one failure and
            // avoids infinite looping on repeat failed attempts.
            throw new OCLCheckerException("OpenCL error", e);
        }
    }

    static void destroy() {
        synchronized (oclLock) {
            // no setup after shutdown: the PoC verifier can still run a turn after destroy()
            initAttempted = true;
            if (initFailure == null) {
                initFailure = new OCLCheckerException("OpenCL was shut down");
            }
            if (program != null) {
                clReleaseProgram(program);
                program = null;
            }
            if (genKernel != null) {
                clReleaseKernel(genKernel);
                genKernel = null;
            }
            if (getKernel != null) {
                clReleaseKernel(getKernel);
                getKernel = null;
            }
            if (getKernel2 != null) {
                clReleaseKernel(getKernel2);
                getKernel2 = null;
            }
            if (queue != null) {
                clReleaseCommandQueue(queue);
                queue = null;
            }
            if (ctx != null) {
                clReleaseContext(ctx);
                ctx = null;
            }
        }
    }

    /** Test-only: forgets a previous setup attempt or shutdown. */
    static void resetForTests() {
        synchronized (oclLock) {
            initAttempted = false;
            initFailure = null;
        }
    }

    private static boolean checkAvailable(cl_device_id device) {
        long[] available = new long[1];
        clGetDeviceInfo(device, CL_DEVICE_AVAILABLE, Sizeof.cl_long, Pointer.to(available), null);
        return available[0] == 1;
    }

    // idk if the kernel works on big endian, but I'm guessing not and I don't have
    // the hardware to
    // find out
    private static boolean checkLittleEndian(cl_device_id device) {
        long[] endianLittle = new long[1];
        clGetDeviceInfo(device, CL_DEVICE_ENDIAN_LITTLE, Sizeof.cl_long, Pointer.to(endianLittle),
                null);
        return endianLittle[0] == 1;
    }

    private static int getComputeUnits(cl_device_id device) {
        int[] maxComputeUnits = new int[1];
        clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS, 4, Pointer.to(maxComputeUnits), null);
        return maxComputeUnits[0];
    }

    private static long calculateMaxItemsByMem(cl_device_id device) {
        long[] globalMemSize = new long[1];
        long[] maxMemAllocSize = new long[1];

        clGetDeviceInfo(device, CL_DEVICE_GLOBAL_MEM_SIZE, 8, Pointer.to(globalMemSize), null);
        clGetDeviceInfo(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE, 8, Pointer.to(maxMemAllocSize), null);

        long maxItemsByGlobalMemSize = (globalMemSize[0] * MEM_PERCENT / 100) / MEM_PER_ITEM;
        long maxItemsByMaxAllocSize = (maxMemAllocSize[0] * MEM_PERCENT / 100) / BUFFER_PER_ITEM;

        logger.debug("Global Memory: {}", globalMemSize[0]);
        logger.debug("Max alloc Memory: {}", maxMemAllocSize[0]);
        logger.debug("maxItemsByGlobalMemSize: {}", maxItemsByGlobalMemSize);
        logger.debug("maxItemsByMaxAllocSize: {}", maxItemsByMaxAllocSize);

        return Math.min(maxItemsByGlobalMemSize, maxItemsByMaxAllocSize);
    }

    private static AutoChooseResult autoChooseDevice() {
        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);

        if (numPlatforms[0] == 0) {
            throw new OCLCheckerException("No OpenCL platforms found");
        }

        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        AutoChooseResult bestResult = null;
        long bestScore = 0;
        boolean intel = false;
        for (int pfi = 0; pfi < platforms.length; pfi++) {
            long[] platformNameSize = new long[1];
            clGetPlatformInfo(platforms[pfi], CL_PLATFORM_NAME, 0, null, platformNameSize);
            byte[] platformNameChars = new byte[(int) platformNameSize[0]];
            clGetPlatformInfo(platforms[pfi], CL_PLATFORM_NAME, platformNameChars.length,
                    Pointer.to(platformNameChars), null);
            String platformName = new String(platformNameChars);

            logger.info("Platform {}: {}", pfi, platformName);

            int[] numDevices = new int[1];
            clGetDeviceIDs(platforms[pfi], CL_DEVICE_TYPE_GPU, 0, null, numDevices);

            if (numDevices[0] == 0) {
                continue;
            }

            cl_device_id[] devices = new cl_device_id[numDevices[0]];
            clGetDeviceIDs(platforms[pfi], CL_DEVICE_TYPE_GPU, devices.length, devices, null);

            for (int dvi = 0; dvi < devices.length; dvi++) {
                if (!checkAvailable(devices[dvi])) {
                    continue;
                }

                if (!checkLittleEndian(devices[dvi])) {
                    continue;
                }

                if (bestResult != null && platformName.toLowerCase(Locale.ENGLISH).contains("intel")) {
                    continue;
                }

                long[] clock = new long[1];
                clGetDeviceInfo(devices[dvi], CL_DEVICE_MAX_CLOCK_FREQUENCY, Sizeof.cl_long,
                        Pointer.to(clock), null);

                long maxItemsAtOnce = Math.min(calculateMaxItemsByMem(devices[dvi]),
                        (long) getComputeUnits(devices[dvi]) * 256);

                long score = maxItemsAtOnce * clock[0];

                if (bestResult == null || score > bestScore || intel) {
                    bestResult = new AutoChooseResult(pfi, dvi);
                    bestScore = score;
                    if (platformName.toLowerCase(Locale.ENGLISH).contains("intel")) {
                        intel = true;
                    }
                }
            }
        }

        return bestResult;
    }

    private static class AutoChooseResult {
        final int platform;
        final int device;

        AutoChooseResult(int platform, int device) {
            this.platform = platform;
            this.device = device;
        }

        int getPlatform() {
            return platform;
        }

        int getDevice() {
            return device;
        }
    }

    public static class OCLCheckerException extends RuntimeException {
        OCLCheckerException(String message) {
            super(message);
        }

        OCLCheckerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PreValidateFailException extends RuntimeException {
        transient final Block block;

        PreValidateFailException(String message, Throwable cause, Block block) {
            super(message, cause);
            this.block = block;
        }

        public Block getBlock() {
            return block;
        }
    }
}
