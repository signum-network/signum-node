package brs.at;

class AtApiController {
    private static final AtApiImpl atApi = new AtApiImpl();

    private AtApiController() {
    }

    public static long func(int funcNum, AtMachineState state) {
        switch (funcNum) {
            case 256:
                return atApi.getA1(state);
            case 257:
                return atApi.getA2(state);
            case 258:
                return atApi.getA3(state);
            case 259:
                return atApi.getA4(state);
            case 260:
                return atApi.getB1(state);
            case 261:
                return atApi.getB2(state);
            case 262:
                return atApi.getB3(state);
            case 263:
                return atApi.getB4(state);
            case 288:
                atApi.clearA(state);
                break;
            case 289:
                atApi.clearB(state);
                break;
            case 290:
                atApi.clearA(state);
                atApi.clearB(state);
                break;
            case 291:
                atApi.copyAFromB(state);
                break;
            case 292:
                atApi.copyBFromA(state);
                break;
            case 293:
                return atApi.checkAIsZero(state);
            case 294:
                return atApi.checkBIsZero(state);
            case 295:
                return atApi.checkAEqualsB(state);
            case 296:
                atApi.swapAAndB(state);
                break;
            case 297:
                atApi.orAWithB(state);
                break;
            case 298:
                atApi.orBWithA(state);
                break;
            case 299:
                atApi.andAWithB(state);
                break;
            case 300:
                atApi.andBWithA(state);
                break;
            case 301:
                atApi.xorAWithB(state);
                break;
            case 302:
                atApi.xorBWithA(state);
                break;
            case 320:
                atApi.addAToB(state);
                break;
            case 321:
                atApi.addBToA(state);
                break;
            case 322:
                atApi.subAFromB(state);
                break;
            case 323:
                atApi.subBFromA(state);
                break;
            case 324:
                atApi.mulAByB(state);
                break;
            case 325:
                atApi.mulBByA(state);
                break;
            case 326:
                atApi.divAByB(state);
                break;
            case 327:
                atApi.divBByA(state);
                break;

            case 512:
                atApi.md5Atob(state);
                break;
            case 513:
                return atApi.checkMd5AWithB(state);
            case 514:
                atApi.hash160AToB(state);
                break;
            case 515:
                return atApi.checkHash160AWithB(state);
            case 516:
                atApi.sha256AToB(state);
                break;
            case 517:
                return atApi.checkSha256AWithB(state);

            case 768:
                return atApi.getBlockTimestamp(state);    // 0x0300
            case 769:
                return atApi.getCreationTimestamp(state); // 0x0301
            case 770:
                return atApi.getLastBlockTimestamp(state);
            case 771:
                atApi.putLastBlockHashInA(state);
                break;
            case 773:
                return atApi.getTypeForTxInA(state);
            case 774:
                return atApi.getAmountForTxInA(state);
            case 775:
                return atApi.getTimestampForTxInA(state);
            case 776:
                return atApi.getRandomIdForTxInA(state);
            case 777:
                atApi.messageFromTxInAToB(state);
                break;
            case 778:
                atApi.bToAddressOfTxInA(state);
                break;
            case 779:
                atApi.bToAddressOfCreator(state);
                break;

            case 1024:
                return atApi.getCurrentBalance(state);
            case 1025:
                return atApi.getPreviousBalance(state);
            case 1027:
                atApi.sendAllToAddressInB(state);
                break;
            case 1028:
                atApi.sendOldToAddressInB(state);
                break;
            case 1029:
                atApi.sendAToAddressInB(state);
                break;
            default:
                return 0;
        }
        return 0;
    }

    public static long func1(int funcNum, long val, AtMachineState state) {
        switch (funcNum) {
            case 272:
                atApi.setA1(val, state);
                break;
            case 273:
                atApi.setA2(val, state);
                break;
            case 274:
                atApi.setA3(val, state);
                break;
            case 275:
                atApi.setA4(val, state);
                break;
            case 278:
                atApi.setB1(val, state);
                break;
            case 279:
                atApi.setB2(val, state);
                break;
            case 280:
                atApi.setB3(val, state);
                break;
            case 281:
                atApi.setB4(val, state);
                break;
            case 772:
                atApi.aToTxAfterTimestamp(val, state);
                break;
            case 1026:
                atApi.sendToAddressInB(val, state);
                break;
            default:
                return 0;
        }
        return 0;
    }

    public static long func2(int funcNum, long val1, long val2, AtMachineState state) {
        switch (funcNum) {
            case 276:
                atApi.setA1A2(val1, val2, state);
                break;
            case 277:
                atApi.setA3A4(val1, val2, state);
                break;
            case 282:
                atApi.setB1B2(val1, val2, state);
                break;
            case 283:
                atApi.setB3B4(val1, val2, state);
                break;
            case 1030:
                return atApi.addMinutesToTimestamp(val1, val2, state);
            default:
                return 0;
        }
        return 0;
    }
}
