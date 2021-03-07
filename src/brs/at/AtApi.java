package brs.at;

interface AtApi {
    // range 0x0100..0x01ff

    /**
     * sets @addr to A1 (0x0100)
     *
     * @param state AT machine state
     * @return A1 register
     */
    long getA1(AtMachineState state);

    /**
     * sets @addr to A2 (0x0101)
     *
     * @param state AT machine state
     * @return A2 register
     */
    long getA2(AtMachineState state);

    /**
     * sets @addr to A3 (0x0102)
     *
     * @param state AT machine state
     * @return A3 register
     */
    long getA3(AtMachineState state);

    /**
     * sets @addr to A4 (0x0103)
     *
     * @param state AT machine state
     * @return A4 register
     */
    long getA4(AtMachineState state);

    /**
     * sets @addr to B1 (0x0104)
     *
     * @param state AT machine state
     * @return B1 register
     */
    long getB1(AtMachineState state);

    /**
     * sets @addr to B2 (0x0105)
     *
     * @param state AT machine state
     * @return B2 register
     */
    long getB2(AtMachineState state);

    /**
     * sets @addr to B3 (0x0106)
     *
     * @param state AT machine state
     * @return B3 register
     */
    long getB3(AtMachineState state);

    /**
     * sets @addr to B4 (0x0107)
     *
     * @param state AT machine state
     * @return B4 register
     */
    long getB4(AtMachineState state);

    /**
     * sets A1 to @addr (0x0110)
     *
     * @param val   new A1 register value
     * @param state AT machine state
     */
    void setA1(long val, AtMachineState state);

    /**
     * sets A2 to @addr (0x0111)
     *
     * @param val   new A2 register value
     * @param state AT machine state
     */
    void setA2(long val, AtMachineState state);

    /**
     * sets A3 to @addr (0x0112)
     *
     * @param val   new A3 register value
     * @param state AT machine state
     */
    void setA3(long val, AtMachineState state);

    /**
     * sets A4 to @addr (0x0113)
     *
     * @param val   new A4 register value
     * @param state AT machine state
     */
    void setA4(long val, AtMachineState state);

    /**
     * sets A1 from @addr1 and A2 from @addr2 (0x0114)
     *
     * @param val1  new A1 register value
     * @param val2  new A2 register value
     * @param state AT machine state
     */
    void setA1A2(long val1, long val2, AtMachineState state);

    /**
     * sets A3 from @addr1 and A4 from @addr2 ((0x0115)
     *
     * @param val1  new A3 register value
     * @param val2  new A4 register value
     * @param state AT machine state
     */
    void setA3A4(long val1, long val2, AtMachineState state);

    /**
     * sets B1 from @addr (0x0116)
     *
     * @param val   new B1 register value
     * @param state AT machine state
     */
    void setB1(long val, AtMachineState state);

    /**
     * sets B2 from @addr (0x0117)
     *
     * @param val   new B2 register value
     * @param state AT machine state
     */
    void setB2(long val, AtMachineState state);

    /**
     * sets B3 from @addr (0x0118)
     *
     * @param val   new B3 register value
     * @param state AT machine state
     */
    void setB3(long val, AtMachineState state);

    /**
     * sets B4 @addr (0x0119)
     *
     * @param val   new B4 register value
     * @param state AT machine state
     */
    void setB4(long val, AtMachineState state);

    /**
     * sets B1 from @addr1 and B2 from @addr2 (0x011a)
     *
     * @param val1  new B1 register value
     * @param val2  new B2 register value
     * @param state AT machine state
     */
    void setB1B2(long val1, long val2, AtMachineState state);

    /**
     * sets B3 from @addr3 and @addr4 to B4 (0x011b)
     *
     * @param val3  new B3 register value
     * @param val4  new B4 register value
     * @param state AT machine state
     */
    void setB3B4(long val3, long val4, AtMachineState state);

    /**
     * sets A to zero (A being A1...4)
     *
     * @param state AT machine state
     */
    void clearA(AtMachineState state);

    /**
     * sets B to zero (B being B1...4)
     *
     * @param state AT machine state
     */
    void clearB(AtMachineState state);

    /**
     * gets A from B
     *
     * @param state AT machine state
     */
    void copyAFromB(AtMachineState state);

    /**
     * gets B from A
     *
     * @param state AT machine state
     */
    void copyBFromA(AtMachineState state);

    /**
     * bool is A is zero
     *
     * @param state AT machine state
     * @return boolean value (in a long?) if A is zero
     */
    long checkAIsZero(AtMachineState state);

    /**
     * bool is B is zero
     *
     * @param state AT machine state
     * @return boolean value (in a long?) if B is zero
     */
    long checkBIsZero(AtMachineState state);


    /**
     * bool does A equal B
     *
     * @param state AT machine state
     * @return boolean value (in a long?) if B is zero
     */
    long checkAEqualsB(AtMachineState state);

    /**
     * swap the values of A and B
     *
     * @param state AT machine state
     */
    void swapAAndB(AtMachineState state);

    // note: these 8 math ops are intended for a future implementaion so no need to support them

    /**
     * adds A to B (result in B)
     *
     * @param state AT machine state
     */
    void addAToB(AtMachineState state);

    /**
     * add B to A (result in A)
     *
     * @param state AT machine state
     */
    void addBToA(AtMachineState state);

    /**
     * subs A from B (result in B)
     *
     * @param state AT machine state
     */
    void subAFromB(AtMachineState state);

    /**
     * subs B from A (result in A)
     *
     * @param state AT machine state
     */
    void subBFromA(AtMachineState state);

    /**
     * multiplies A by B (result in B)
     *
     * @param state AT machine state
     */
    void mulAByB(AtMachineState state);

    /**
     * multiplies B by A (result in A)
     *
     * @param state AT machine state
     */
    void mulBByA(AtMachineState state);

    /**
     * divides A by B (result in B) *can cause a divide by zero error which would stop the machine
     *
     * @param state AT machine state
     */
    void divAByB(AtMachineState state);

    /**
     * divides B by A (result in A) *can cause a divide by zero error which would stop the machine
     *
     * @param state AT machine state
     */
    void divBByA(AtMachineState state);

    /**
     * ors A by B (result in A)
     *
     * @param state AT machine state
     */
    void orAWithB(AtMachineState state);

    /**
     * ors B by A (result in B)
     *
     * @param state AT machine state
     */
    void orBWithA(AtMachineState state);

    /**
     * ands A by B (result in A)
     *
     * @param state AT machine state
     */
    void andAWithB(AtMachineState state);

    /**
     * ands B by A (result in B)
     *
     * @param state AT machine state
     */
    void andBWithA(AtMachineState state);

    /**
     * xors A by B (result in A)
     *
     * @param state AT machine state
     */
    void xorAWithB(AtMachineState state);

    /**
     * xors B by A (result in B)
     *
     * @param state AT machine state
     */
    void xorBWithA(AtMachineState state);

    // end range 0x0100..0x01ff

    // range 0x0200..0x02ff

    /**
     * sets @addr1 and @addr2 to the MD5 hash of A1..4
     *
     * @param state AT machine state
     */
    void md5Atob(AtMachineState state);

    /**
     * bool if @addr1 and @addr2 matches the MD5 hash of A1..4
     *
     * @param state AT machine state
     * @return bool if @addr1 and @addr2 matches the MD5 hash of A1..4
     */
    long checkMd5AWithB(AtMachineState state);

    /**
     * take a RIPEMD160 hash of A1..4 and put this in B1..4
     *
     * @param state AT machine state
     */
    void hash160AToB(AtMachineState state);

    /**
     * bool if RIPEMD160 hash of A1..4 matches B1..4
     *
     * @param state AT machine state
     * @return bool if RIPEMD160 hash of A1..4 matches B1..4
     */
    long checkHash160AWithB(AtMachineState state);

    /**
     * take a SHA256 hash of A1..4 abd out this in B1..4
     *
     * @param state AT machine state
     */
    void sha256AToB(AtMachineState state);

    /**
     * bool if SHA256 of A1..4 matches B1..4
     *
     * @param state AT machine state
     * @return bool if SHA256 of A1..4 matches B1..4
     */
    long checkSha256AWithB(AtMachineState state);

    // end of range 0x02..0x02ff

    // range 0x03..0x03ff

    /**
     * sets @addr to the timestamp of the current block
     *
     * @param state AT machine state
     */
    long getBlockTimestamp(AtMachineState state);

    /**
     * sets @addr to the timestamp of the AT creation block
     *
     * @param state AT machine state
     */
    long getCreationTimestamp(AtMachineState state);


    /**
     * sets @addr to the timestamp of the previous block
     *
     * @param state AT machine state
     * @return timestamp of the previous block
     */
    long getLastBlockTimestamp(AtMachineState state);

    /**
     * puts the block hash of the previous block in A
     *
     * @param state AT machine state
     */
    void putLastBlockHashInA(AtMachineState state);

    /**
     * sets A to zero/tx hash of the first tx after
     *
     * @param state AT machine state
     */
    void aToTxAfterTimestamp(long val, AtMachineState state);

    /**
     * @param state AT machine state
     * @return bool if A is a valid tx with @addr to tx type
     * 0: normal tx
     * 1: message tx
     */
    long getTypeForTxInA(AtMachineState state);

    /**
     * @param state AT machine state
     * @return bool if A is a valid tx with @addr to tx amount
     */
    long getAmountForTxInA(AtMachineState state);

    /**
     * @param state AT machine state
     * @return bool if A is a valid tx with @addr to tx asset id
     */
    long GetAssetIdForTxInA(AtMachineState state);

    /**
     * @param state AT machine state
     * @return bool if A is a valid tx with @addr to tx asset amount
     */
    long GetAssetAmountForTxInA(AtMachineState state);

     /**
     * @param state AT machine state
     * @return bool if Mold is done successfully set A to the asset id, asset information in  B1(assetDecimals) B2(assetQuantity) A1~4(assetDesc) B3-4(assetName)
     */
    long Mold(AtMachineState state);

     /**
     * @param state AT machine state
     * @return bool if B1 is a valid address then send it the amount, asset and msg.  B1(address) B2(asset amount) A1~4(message) B3(asset id) B4(amount)
     */
    long Mint(AtMachineState state);

    /**
     * @param state AT machine state
     * @return bool if A is a valid tx with @addr to the tx timestamp
     */
    long getTimestampForTxInA(AtMachineState state);

    /**
     * @param state AT machine state
     * @return bool if A is a valid tx with @addr to the tx random id
     * random id is a 64bit signed value (always positive) and this is a blocking function
     */
    long getRandomIdForTxInA(AtMachineState state);

    /**
     * bool if A is a valid tx with B to the tx message
     * if a tx is not a message tx then this will zero out the B value
     *
     * @param state AT machine state
     */
    void messageFromTxInAToB(AtMachineState state);

    /**
     * bool if A is a valid tx with B set to the tx address
     *
     * @param state AT machine state
     */
    void bToAddressOfTxInA(AtMachineState state);

    /**
     * set B to the address of the AT's creator
     *
     * @param state AT machine state
     */
    void bToAddressOfCreator(AtMachineState state);

    // end range 0x0300..0x03ff
    // ------------------------

    // ------------------------
    // range 0x0400..0x04ff

    /**
     * sets @addr to current balance of the AT
     *
     * @param state AT machine state
     */
    long getCurrentBalance(AtMachineState state);

    /**
     * sets @addr to the balance it had last had when running
     * this amount does not include any additional amounts sent to the
     * AT between "execution events"
     *
     * @param state AT machine state
     */
    long getPreviousBalance(AtMachineState state);

    /**
     * bool if B is a valid address then send it $addr amount
     * if this amount is greater than the AT's balance then it will also
     * return false
     *
     * @param state AT machine state
     */
    void sendToAddressInB(long val, AtMachineState state);

    /**
     * bool if B is a valid address then send it entire balance
     *
     * @param state AT machine state
     */
    void sendAllToAddressInB(AtMachineState state);

    /**
     * bool if B is a valid address then send it the old balance
     *
     * @param state AT machine state
     */
    void sendOldToAddressInB(AtMachineState state);

    /**
     * bool if B is valid address then send it A as a message
     *
     * @param state AT machine state
     */
    void sendAToAddressInB(AtMachineState state);

    /**
     * $addr1 is timestamp calculated from $addr2
     *
     * @param state AT machine state
     * @return time+minutes
     */
    long addMinutesToTimestamp(long val1, long val2, AtMachineState state);

    /**
     * set min amount of balance increase needed to unfreeze
     *
     * @param state AT machine state
     */
    void setMinActivationAmount(long val, AtMachineState state);

    // end range 0x0400.0x04ff
    // -----------------------

    /**
     * puts the gensig of the previous block in A
     *
     * @param state AT machine state
     */
    void putLastBlockGenerationSignatureInA(AtMachineState state);

    /**
     * take a SHA256 hash of val2 bytes starting at val1. out this in B1..4
     *
     * @param state AT machine state
     */
    void sha256ToB(long val1, long val2, AtMachineState state);
}
